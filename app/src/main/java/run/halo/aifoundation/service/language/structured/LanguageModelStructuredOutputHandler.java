package run.halo.aifoundation.service.language.structured;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.exception.StructuredOutputTerminationException;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.service.language.mapping.LanguageModelResponseMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class LanguageModelStructuredOutputHandler {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {
    };

    private final LanguageModelResponseMapper responseMapper;
    private final JsonWriter jsonWriter;

    public LanguageModelStructuredOutputHandler(LanguageModelResponseMapper responseMapper,
        JsonWriter jsonWriter) {
        this.responseMapper = responseMapper;
        this.jsonWriter = jsonWriter;
    }

    public String instruction(OutputSpec output) {
        return instruction(output, false);
    }

    public String instruction(OutputSpec output, boolean includeExample) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return null;
        }
        var base = "Return only the requested structured output. Do not wrap it in Markdown or "
            + "explanatory prose.";
        var instruction = switch (output.getType()) {
            case OBJECT -> base + " Return a JSON object that matches this JSON Schema: "
                + jsonWriter.write(output.getSchema());
            case ARRAY -> base + " Return a JSON array. Each element must match this JSON Schema: "
                + jsonWriter.write(output.getElementSchema());
            case CHOICE -> base + " Return exactly one of these string choices: "
                + String.join(", ", output.getChoices());
            case JSON -> base + " Return valid JSON.";
            case TEXT -> null;
        };
        if (!includeExample) {
            return instruction;
        }
        var example = exampleForOutput(output);
        return example != null
            ? instruction + " Example: " + jsonWriter.write(example)
            : instruction;
    }

    private Object exampleForOutput(OutputSpec output) {
        return switch (output.getType()) {
            case OBJECT -> exampleForSchema(output.getSchema());
            case ARRAY -> {
                var element = exampleForSchema(output.getElementSchema());
                yield element != null ? List.of(element) : List.of();
            }
            case CHOICE -> output.getChoices() != null && !output.getChoices().isEmpty()
                ? output.getChoices().getFirst()
                : null;
            case JSON -> Map.of();
            case TEXT -> null;
        };
    }

    private Object exampleForSchema(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        if (schema.get("anyOf") instanceof List<?> alternatives) {
            for (var alternative : alternatives) {
                if (alternative instanceof Map<?, ?> alternativeSchema) {
                    @SuppressWarnings("unchecked")
                    var typedSchema = (Map<String, Object>) alternativeSchema;
                    var example = exampleForSchema(typedSchema);
                    if (example != null) {
                        return example;
                    }
                }
            }
            return null;
        }
        if (schema.get("enum") instanceof List<?> values && !values.isEmpty()) {
            return values.getFirst();
        }
        var type = schema.get("type");
        if (type instanceof List<?> types) {
            type = types.stream().filter(value -> !"null".equals(value)).findFirst().orElse(null);
        }
        if ("object".equals(type) || schema.get("properties") instanceof Map<?, ?>) {
            var example = new LinkedHashMap<String, Object>();
            if (schema.get("properties") instanceof Map<?, ?> properties) {
                properties.forEach((name, propertySchema) -> {
                    if (name != null && propertySchema instanceof Map<?, ?> valueSchema) {
                        @SuppressWarnings("unchecked")
                        var typedSchema = (Map<String, Object>) valueSchema;
                        example.put(name.toString(), exampleForSchema(typedSchema));
                    }
                });
            }
            return example;
        }
        if ("array".equals(type)) {
            return List.of();
        }
        if ("boolean".equals(type)) {
            return false;
        }
        if ("integer".equals(type) || "number".equals(type)) {
            return 0;
        }
        if ("null".equals(type)) {
            return null;
        }
        return "";
    }

    public StructuredOutput parse(OutputSpec output, String text) {
        return parse(output, text, null);
    }

    public StructuredOutput parse(OutputSpec output, String text, String diagnosticId) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return new StructuredOutput(text, text);
        }
        var outputText = outputText(output, text);
        traceParseInput(output, text, outputText, diagnosticId);
        try {
            var structuredOutput = switch (output.getType()) {
                case JSON -> new StructuredOutput(JSON_MAPPER.readValue(outputText, OBJECT_TYPE),
                    outputText);
                case OBJECT -> {
                    var value = JSON_MAPPER.readValue(outputText, OBJECT_TYPE);
                    if (!(value instanceof Map<?, ?> map)) {
                        throw validationError(
                            "Structured output validation failed: expected JSON object", "$");
                    }
                    var sanitized = responseMapper.sanitizeValue(map);
                    validateJsonValue(sanitized, output.getSchema(), "$");
                    yield new StructuredOutput(sanitized, outputText);
                }
                case ARRAY -> {
                    var value = JSON_MAPPER.readValue(outputText, OBJECT_TYPE);
                    if (!(value instanceof List<?> list)) {
                        throw validationError(
                            "Structured output validation failed: expected JSON array", "$");
                    }
                    for (var i = 0; i < list.size(); i++) {
                        validateJsonValue(list.get(i), output.getElementSchema(), "$[" + i + "]");
                    }
                    yield new StructuredOutput(responseMapper.sanitizeValue(list), outputText);
                }
                case CHOICE -> {
                    var choice = normalizeChoice(outputText);
                    if (output.getChoices() == null || !output.getChoices().contains(choice)) {
                        throw validationError(
                            "Structured output validation failed: expected one of "
                                + output.getChoices(), "$");
                    }
                    yield new StructuredOutput(choice, outputText);
                }
                case TEXT -> new StructuredOutput(text, text);
            };
            traceParseSuccess(output, structuredOutput, diagnosticId);
            return structuredOutput;
        } catch (StructuredOutputValidationException e) {
            traceParseFailure(output, outputText, diagnosticId, "schema-validation", e);
            throw e;
        } catch (JacksonException e) {
            var validationError = new StructuredOutputValidationException(
                "Structured output validation failed: output is not valid JSON", e,
                null, null, "$", null, null, null);
            traceParseFailure(output, outputText, diagnosticId, "json-parse", validationError);
            throw validationError;
        }
    }

    private void traceParseInput(OutputSpec output, String text, String outputText,
        String diagnosticId) {
        AiFoundationDiagnostics.trace("structured-output-input", diagnosticId,
            () -> AiFoundationDiagnostics.fields(
                "outputType", output.getType(),
                "strict", output.getStrict(),
                "schema", outputSchema(output),
                "modelText", text,
                "extractedText", outputText));
    }

    private void traceParseSuccess(OutputSpec output, StructuredOutput structuredOutput,
        String diagnosticId) {
        AiFoundationDiagnostics.trace("structured-output-success", diagnosticId,
            () -> AiFoundationDiagnostics.fields(
                "outputType", output.getType(),
                "parsedValue", jsonWriter.write(structuredOutput.output()),
                "outputText", structuredOutput.outputText()));
    }

    private void traceParseFailure(OutputSpec output, String outputText, String diagnosticId,
        String stage, StructuredOutputValidationException error) {
        AiFoundationDiagnostics.trace("structured-output-failure", diagnosticId,
            () -> AiFoundationDiagnostics.fields(
                "outputType", output.getType(),
                "strict", output.getStrict(),
                "schema", outputSchema(output),
                "stage", stage,
                "validationPath", error.getValidationPath(),
                "outputText", outputText,
                "message", error.getMessage()));
    }

    private String outputSchema(OutputSpec output) {
        return switch (output.getType()) {
            case OBJECT -> jsonWriter.write(output.getSchema());
            case ARRAY -> jsonWriter.write(output.getElementSchema());
            case CHOICE -> jsonWriter.write(output.getChoices());
            case JSON, TEXT -> null;
        };
    }

    public Flux<Object> partialOutputStream(GenerateTextRequest request, Flux<String> textStream) {
        if (!hasStructuredOutput(request)
            || (request.getOutput().getType() != OutputType.OBJECT
            && request.getOutput().getType() != OutputType.JSON)) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            var observer = new StructuredStreamObserver(request.getOutput());
            return textStream.handle((delta, sink) -> {
                var partial = observer.partial(delta);
                if (partial != null) {
                    sink.next(partial);
                }
            });
        });
    }

    public Flux<Object> elementStream(GenerateTextRequest request, Flux<String> textStream) {
        if (!hasStructuredOutput(request) || request.getOutput().getType() != OutputType.ARRAY) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            var observer = new StructuredStreamObserver(request.getOutput());
            return textStream.concatMap(delta -> Flux.fromIterable(observer.elements(delta)));
        });
    }

    public StructuredOutputValidationException enrich(StructuredOutputValidationException error,
        OutputSpec output, String outputText, Integer stepIndex, LanguageModelUsage usage,
        GenerationResponseMetadata response) {
        return enrich(error, output, outputText, stepIndex, usage, response, FinishReason.UNKNOWN,
            null);
    }

    public StructuredOutputValidationException enrich(StructuredOutputValidationException error,
        OutputSpec output, String outputText, Integer stepIndex, LanguageModelUsage usage,
        GenerationResponseMetadata response, FinishReason finishReason, String rawFinishReason) {
        if (isExplicitAbnormalFinish(finishReason)) {
            var termination = new StructuredOutputTerminationException(
                terminationMessage(finishReason, rawFinishReason), error,
                output != null ? output.getType() : error.getOutputType(),
                error.getOutputText() != null ? error.getOutputText() : outputText,
                error.getValidationPath(), stepIndex, usage, response, finishReason,
                rawFinishReason);
            AiFoundationDiagnostics.warnStructuredOutputFailure(termination, finishReason,
                rawFinishReason);
            traceTermination(termination, response);
            return termination;
        }
        var validationError = new StructuredOutputValidationException(error.getMessage(), error,
            output != null ? output.getType() : error.getOutputType(),
            error.getOutputText() != null ? error.getOutputText() : outputText,
            error.getValidationPath(), stepIndex, usage, response);
        AiFoundationDiagnostics.warnStructuredOutputFailure(validationError, finishReason,
            rawFinishReason);
        return validationError;
    }

    private boolean isExplicitAbnormalFinish(FinishReason finishReason) {
        return finishReason != null
            && finishReason != FinishReason.STOP
            && finishReason != FinishReason.UNKNOWN;
    }

    private String terminationMessage(FinishReason finishReason, String rawFinishReason) {
        return switch (finishReason) {
            case LENGTH -> "Structured output generation reached the output token limit before a "
                + "valid result was produced";
            case CONTENT_FILTER -> "Structured output generation was stopped by the provider content "
                + "filter before a valid result was produced";
            case TOOL_CALLS -> "Structured output generation stopped for tool calls before a final "
                + "valid result was produced";
            case ERROR -> "Structured output generation ended with a provider error before a valid "
                + "result was produced";
            case OTHER -> "Structured output generation stopped with provider finish reason '"
                + (rawFinishReason != null && !rawFinishReason.isBlank()
                    ? rawFinishReason : "other")
                + "' before a valid result was produced";
            default -> "Structured output generation stopped before a valid result was produced";
        };
    }

    private void traceTermination(StructuredOutputTerminationException error,
        GenerationResponseMetadata response) {
        AiFoundationDiagnostics.trace("structured-output-termination", diagnosticId(response),
            () -> AiFoundationDiagnostics.fields(
                "outputType", error.getOutputType(),
                "finishReason", error.getFinishReason(),
                "rawFinishReason", error.getRawFinishReason(),
                "validationPath", error.getValidationPath(),
                "outputText", error.getOutputText(),
                "usage", error.getUsage(),
                "message", error.getMessage()));
    }

    private String diagnosticId(GenerationResponseMetadata response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        var value = response.getMetadata().get(AiFoundationDiagnostics.CORRELATION_ID_KEY);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public void validateJsonValue(Object value, Map<String, Object> schema, String path) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        validateAnyOf(value, schema.get("anyOf"), path);
        var type = schema.get("type");
        if (type instanceof String typeName) {
            validateJsonType(value, typeName, path);
        } else if (type instanceof Collection<?> typeNames
            && typeNames.stream().map(String::valueOf)
                .noneMatch(typeName -> matchesJsonType(value, typeName))) {
            throw validationError(
                "Structured output validation failed: " + path + " must match one of "
                    + typeNames,
                path);
        }
        var enumValues = schema.get("enum");
        if (enumValues instanceof Collection<?> values && !values.contains(value)) {
            throw validationError(
                "Structured output validation failed: " + path + " must be one of " + values,
                path);
        }
        var nullableNull = value == null && hasSchemaType(type, "null");
        if (!nullableNull
            && (hasSchemaType(type, "object") || schema.containsKey("properties"))) {
            validateObjectValue(value, schema, path);
        }
        if (!nullableNull
            && (hasSchemaType(type, "array") || schema.containsKey("items"))) {
            validateArrayValue(value, schema, path);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateAnyOf(Object value, Object anyOf, String path) {
        if (anyOf == null) {
            return;
        }
        if (!(anyOf instanceof Collection<?> alternatives) || alternatives.isEmpty()) {
            throw validationError(
                "Structured output validation failed: " + path
                    + " has an invalid anyOf schema",
                path);
        }
        for (var alternative : alternatives) {
            if (!(alternative instanceof Map<?, ?> alternativeSchema)) {
                continue;
            }
            try {
                validateJsonValue(value,
                    (Map<String, Object>) responseMapper.sanitizeValue(alternativeSchema), path);
                return;
            } catch (StructuredOutputValidationException ignored) {
                // Try the next alternative before reporting the union failure.
            }
        }
        throw validationError(
            "Structured output validation failed: " + path
                + " does not match any allowed schema",
            path);
    }

    @SuppressWarnings("unchecked")
    private void validateObjectValue(Object value, Map<String, Object> schema, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw validationError(
                "Structured output validation failed: " + path + " must be an object", path);
        }
        var required = schema.get("required");
        if (required instanceof Collection<?> requiredFields) {
            validateRequiredFields(map, requiredFields, path);
        }
        var properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            for (var entry : propertyMap.entrySet()) {
                var key = entry.getKey();
                if (key == null || !map.containsKey(key)) {
                    continue;
                }
                if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                    validateJsonValue(map.get(key),
                        (Map<String, Object>) responseMapper.sanitizeValue(propertySchema),
                        path + "." + key);
                }
            }
        }
    }

    private void validateRequiredFields(Map<?, ?> map, Collection<?> requiredFields, String path) {
        for (var field : requiredFields) {
            if (!map.containsKey(field)) {
                throw validationError(
                    "Structured output validation failed: missing required field "
                        + path + "." + field, path + "." + field);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateArrayValue(Object value, Map<String, Object> schema, String path) {
        if (!(value instanceof List<?> list)) {
            throw validationError(
                "Structured output validation failed: " + path + " must be an array", path);
        }
        var items = schema.get("items");
        if (items instanceof Map<?, ?> itemSchema) {
            for (var i = 0; i < list.size(); i++) {
                validateJsonValue(list.get(i),
                    (Map<String, Object>) responseMapper.sanitizeValue(itemSchema),
                    path + "[" + i + "]");
            }
        }
    }

    private String outputText(OutputSpec output, String text) {
        var trimmed = stripMarkdownFence(text);
        var objectText = structuredSlice(trimmed, '{', '}',
            output.getType() == OutputType.OBJECT
                || output.getType() == OutputType.JSON && trimmed.startsWith("{"));
        if (objectText != null) {
            return objectText;
        }
        var arrayText = structuredSlice(trimmed, '[', ']',
            output.getType() == OutputType.ARRAY
                || output.getType() == OutputType.JSON && trimmed.startsWith("["));
        return arrayText != null ? arrayText : trimmed;
    }

    private String stripMarkdownFence(String text) {
        var trimmed = text != null ? text.trim() : "";
        if (trimmed.startsWith("```")) {
            return trimmed.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        }
        return trimmed;
    }

    private String structuredSlice(String text, char startChar, char endChar, boolean enabled) {
        if (!enabled) {
            return null;
        }
        var start = text.indexOf(startChar);
        var end = text.lastIndexOf(endChar);
        if (start >= 0 && end >= start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private String normalizeChoice(String outputText) {
        try {
            var value = JSON_MAPPER.readValue(outputText, OBJECT_TYPE);
            if (value instanceof String text) {
                return text.trim();
            }
        } catch (JacksonException ignored) {
        }
        return outputText.trim();
    }

    private void validateJsonType(Object value, String type, String path) {
        if (!matchesJsonType(value, type)) {
            throw validationError(
                "Structured output validation failed: " + path + " must be " + type, path);
        }
    }

    private boolean matchesJsonType(Object value, String type) {
        return switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            default -> true;
        };
    }

    private boolean hasSchemaType(Object type, String expected) {
        return expected.equals(type)
            || type instanceof Collection<?> types && types.contains(expected);
    }

    private StructuredOutputValidationException validationError(String message,
        String validationPath) {
        return new StructuredOutputValidationException(message, null, null, null, validationPath,
            null, null, null);
    }

    private boolean hasStructuredOutput(GenerateTextRequest request) {
        return request != null
            && request.getOutput() != null
            && request.getOutput().getType() != null
            && request.getOutput().getType() != OutputType.TEXT;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    public interface JsonWriter {
        String write(Object value);
    }

    private final class StructuredStreamObserver {
        private final OutputSpec output;
        private final StringBuilder text = new StringBuilder();
        private String lastPartialJson;
        private int emittedElements;

        StructuredStreamObserver(OutputSpec output) {
            this.output = output;
        }

        Object partial(String delta) {
            text.append(delta);
            var candidate = outputText(output, text.toString());
            if (!hasText(candidate) || candidate.equals(lastPartialJson)) {
                return null;
            }
            try {
                var value = JSON_MAPPER.readValue(candidate, OBJECT_TYPE);
                lastPartialJson = candidate;
                return responseMapper.sanitizeValue(value);
            } catch (JacksonException ignored) {
                return null;
            }
        }

        List<Object> elements(String delta) {
            text.append(delta);
            var elements = completedArrayElements(text.toString());
            if (elements.size() <= emittedElements) {
                return List.of();
            }
            var next = new ArrayList<Object>();
            for (var i = emittedElements; i < elements.size(); i++) {
                var value = elements.get(i);
                validateJsonValue(value, output.getElementSchema(), "$[" + i + "]");
                next.add(responseMapper.sanitizeValue(value));
            }
            emittedElements = elements.size();
            return next;
        }

        private List<Object> completedArrayElements(String source) {
            var trimmed = source != null ? source.trim() : "";
            var start = trimmed.indexOf('[');
            if (start < 0) {
                return List.of();
            }
            var elements = new ArrayList<Object>();
            var elementStart = start + 1;
            var depth = 1;
            var inString = false;
            var escaped = false;
            for (var i = start + 1; i < trimmed.length(); i++) {
                var c = trimmed.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                    continue;
                }
                if (c == '{' || c == '[') {
                    depth++;
                    continue;
                }
                if (c == '}' || c == ']') {
                    depth--;
                    if (depth == 0) {
                        addCompletedElement(elements, trimmed.substring(elementStart, i).trim());
                        return elements;
                    }
                    continue;
                }
                if (c == ',' && depth == 1) {
                    addCompletedElement(elements, trimmed.substring(elementStart, i).trim());
                    elementStart = i + 1;
                }
            }
            return elements;
        }

        private void addCompletedElement(List<Object> elements, String json) {
            if (!hasText(json)) {
                return;
            }
            try {
                elements.add(JSON_MAPPER.readValue(json, OBJECT_TYPE));
            } catch (JacksonException ignored) {
            }
        }
    }
}
