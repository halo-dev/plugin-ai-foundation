package run.halo.aifoundation.provider.protocol.chatcompletions;

import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Pattern;
import run.halo.aifoundation.exception.StructuredOutputSchemaException;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions.ResponseFormat;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.schema.OutputType;

/** Applies Halo structured-output hints to the Chat Completions wire shape. */
public final class ChatCompletionsStructuredOutputOptions {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Pattern RESPONSE_FORMAT_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String DEFAULT_RESPONSE_FORMAT_NAME = "structured_output";

    private ChatCompletionsStructuredOutputOptions() {
    }

    public static void apply(ChatCompletionsOptions.Builder builder, GenerateTextRequest request) {
        apply(builder, request, StructuredOutputSupport.JSON_SCHEMA);
    }

    public static void apply(ChatCompletionsOptions.Builder builder, GenerateTextRequest request,
        StructuredOutputSupport support) {
        var output = request.getOutput();
        if (output == null) {
            return;
        }
        if (output.getType() == null) {
            return;
        }
        if (output.getType() == OutputType.TEXT) {
            return;
        }
        if (support == StructuredOutputSupport.PROMPT_ONLY) {
            return;
        }
        if (usesJsonObject(output.getType(), support)) {
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
            ? ChatCompletionsStrictSchemaValidator.validateAndBuildSchema(output)
            : ChatCompletionsStrictSchemaValidator.buildSchema(output);
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

    private static boolean usesJsonObject(OutputType outputType,
        StructuredOutputSupport support) {
        if (outputType == OutputType.JSON) {
            return true;
        }
        if (support != StructuredOutputSupport.JSON_OBJECT) {
            return false;
        }
        return outputType == OutputType.OBJECT;
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

    public static ChatCompletionsOptions buildBasic(GenerateTextRequest request) {
        var builder = ChatCompletionsOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .customHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
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
