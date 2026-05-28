package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.GenerationResponseMetadata;
import run.halo.aifoundation.LanguageModelUsage;
import run.halo.aifoundation.OutputSpec;
import run.halo.aifoundation.OutputType;
import run.halo.aifoundation.StructuredOutputValidationException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class LanguageModelStructuredOutputHandler {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {
    };

    private final LanguageModelResponseMapper responseMapper;
    private final JsonWriter jsonWriter;

    LanguageModelStructuredOutputHandler(LanguageModelResponseMapper responseMapper,
        JsonWriter jsonWriter) {
        this.responseMapper = responseMapper;
        this.jsonWriter = jsonWriter;
    }

    String instruction(OutputSpec output) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return null;
        }
        var base = "Return only the requested structured output. Do not wrap it in Markdown or "
            + "explanatory prose.";
        return switch (output.getType()) {
            case OBJECT -> base + " Return a JSON object that matches this JSON Schema: "
                + jsonWriter.write(output.getSchema());
            case ARRAY -> base + " Return a JSON array. Each element must match this JSON Schema: "
                + jsonWriter.write(output.getElementSchema());
            case CHOICE -> base + " Return exactly one of these string choices: "
                + String.join(", ", output.getChoices());
            case JSON -> base + " Return valid JSON.";
            case TEXT -> null;
        };
    }

    StructuredOutput parse(OutputSpec output, String text) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return new StructuredOutput(text, text);
        }
        var outputText = outputText(output, text);
        try {
            return switch (output.getType()) {
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
        } catch (StructuredOutputValidationException e) {
            throw e;
        } catch (JacksonException e) {
            throw new StructuredOutputValidationException(
                "Structured output validation failed: output is not valid JSON", e,
                null, null, "$", null, null, null);
        }
    }

    Flux<Object> partialOutputStream(GenerateTextRequest request, Flux<String> textStream) {
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

    Flux<Object> elementStream(GenerateTextRequest request, Flux<String> textStream) {
        if (!hasStructuredOutput(request) || request.getOutput().getType() != OutputType.ARRAY) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            var observer = new StructuredStreamObserver(request.getOutput());
            return textStream.concatMap(delta -> Flux.fromIterable(observer.elements(delta)));
        });
    }

    StructuredOutputValidationException enrich(StructuredOutputValidationException error,
        OutputSpec output, String outputText, Integer stepIndex, LanguageModelUsage usage,
        GenerationResponseMetadata response) {
        return new StructuredOutputValidationException(error.getMessage(), error,
            output != null ? output.getType() : error.getOutputType(),
            error.getOutputText() != null ? error.getOutputText() : outputText,
            error.getValidationPath(), stepIndex, usage, response);
    }

    @SuppressWarnings("unchecked")
    void validateJsonValue(Object value, Map<String, Object> schema, String path) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        var type = schema.get("type");
        if (type instanceof String typeName) {
            validateJsonType(value, typeName, path);
        }
        var enumValues = schema.get("enum");
        if (enumValues instanceof Collection<?> values && !values.contains(value)) {
            throw validationError(
                "Structured output validation failed: " + path + " must be one of " + values,
                path);
        }
        if ("object".equals(type) || schema.containsKey("properties")) {
            if (!(value instanceof Map<?, ?> map)) {
                throw validationError(
                    "Structured output validation failed: " + path + " must be an object", path);
            }
            var required = schema.get("required");
            if (required instanceof Collection<?> requiredFields) {
                for (var field : requiredFields) {
                    if (!map.containsKey(field)) {
                        throw validationError(
                            "Structured output validation failed: missing required field "
                                + path + "." + field, path + "." + field);
                    }
                }
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
        if ("array".equals(type) || schema.containsKey("items")) {
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
    }

    private String outputText(OutputSpec output, String text) {
        var trimmed = text != null ? text.trim() : "";
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        }
        if (output.getType() == OutputType.OBJECT
            || output.getType() == OutputType.JSON && trimmed.startsWith("{")) {
            var start = trimmed.indexOf('{');
            var end = trimmed.lastIndexOf('}');
            if (start >= 0 && end >= start) {
                return trimmed.substring(start, end + 1);
            }
        }
        if (output.getType() == OutputType.ARRAY
            || output.getType() == OutputType.JSON && trimmed.startsWith("[")) {
            var start = trimmed.indexOf('[');
            var end = trimmed.lastIndexOf(']');
            if (start >= 0 && end >= start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
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
        var valid = switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            default -> true;
        };
        if (!valid) {
            throw validationError(
                "Structured output validation failed: " + path + " must be " + type, path);
        }
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
    interface JsonWriter {
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
