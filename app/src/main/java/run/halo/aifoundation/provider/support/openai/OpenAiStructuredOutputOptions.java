package run.halo.aifoundation.provider.support.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Pattern;
import run.halo.aifoundation.exception.StructuredOutputSchemaException;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions.ResponseFormat;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.schema.OutputType;

/**
 * Applies Halo structured output hints to OpenAI-compatible chat options.
 */
public final class OpenAiStructuredOutputOptions {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Pattern RESPONSE_FORMAT_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String DEFAULT_RESPONSE_FORMAT_NAME = "structured_output";

    private OpenAiStructuredOutputOptions() {
    }

    public static void apply(OpenAiCompatibleChatOptions.Builder builder, GenerateTextRequest request) {
        apply(builder, request, StructuredOutputSupport.JSON_SCHEMA);
    }

    public static void apply(OpenAiCompatibleChatOptions.Builder builder, GenerateTextRequest request,
        StructuredOutputSupport support) {
        var output = request.getOutput();
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return;
        }
        if (support == StructuredOutputSupport.PROMPT_ONLY) {
            return;
        }
        if (output.getType() == OutputType.JSON
            || support == StructuredOutputSupport.JSON_OBJECT
            && output.getType() == OutputType.OBJECT) {
            builder.responseFormat(ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_OBJECT)
                .build());
            return;
        }
        if (support != StructuredOutputSupport.JSON_SCHEMA) {
            return;
        }
        if (output.getType() != OutputType.OBJECT) {
            return;
        }
        var strict = Boolean.TRUE.equals(output.getStrict());
        var schema = strict
            ? OpenAiStrictSchemaValidator.validateAndBuildSchema(output)
            : OpenAiStrictSchemaValidator.buildSchema(output);
        if (schema != null) {
            builder.responseFormat(ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(writeJson(Map.copyOf(schema)))
                .name(validateName(output.getName(), output.getType()))
                .description(output.getDescription())
                .strict(strict)
                .build());
        }
    }

    private static String validateName(String name, OutputType outputType) {
        var effectiveName = name == null || name.isBlank() ? DEFAULT_RESPONSE_FORMAT_NAME : name;
        if (!RESPONSE_FORMAT_NAME.matcher(effectiveName).matches()) {
            throw new StructuredOutputSchemaException(
                "Structured output name must contain 1-64 letters, numbers, underscores, or "
                    + "hyphens at $.name",
                outputType,
                "$.name");
        }
        return effectiveName;
    }

    public static OpenAiCompatibleChatOptions buildBasic(GenerateTextRequest request) {
        return buildBasic(request, ReasoningControlOptions.unsupported());
    }

    public static OpenAiCompatibleChatOptions buildBasic(GenerateTextRequest request,
        ReasoningControlOptions reasoningControlOptions) {
        var builder = OpenAiCompatibleChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .customHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
        reasoningControlOptions.apply(builder, request);
        apply(builder, request);
        return builder.build();
    }

    private static String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize structured output schema", e);
        }
    }
}
