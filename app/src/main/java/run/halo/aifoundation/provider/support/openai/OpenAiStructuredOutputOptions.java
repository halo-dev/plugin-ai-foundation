package run.halo.aifoundation.provider.support.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions.ResponseFormat;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.schema.OutputType;

/**
 * Applies Halo structured output hints to OpenAI-compatible chat options.
 */
public final class OpenAiStructuredOutputOptions {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private OpenAiStructuredOutputOptions() {
    }

    public static void apply(OpenAiCompatibleChatOptions.Builder builder, GenerateTextRequest request) {
        var output = request.getOutput();
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return;
        }
        if (output.getType() == OutputType.JSON) {
            return;
        }
        var schema = switch (output.getType()) {
            case OBJECT -> output.getSchema();
            case ARRAY -> {
                var arraySchema = new LinkedHashMap<String, Object>();
                arraySchema.put("type", "array");
                if (output.getElementSchema() != null) {
                    arraySchema.put("items", output.getElementSchema());
                }
                yield arraySchema;
            }
            case CHOICE -> {
                var choiceSchema = new LinkedHashMap<String, Object>();
                choiceSchema.put("type", "string");
                choiceSchema.put("enum", output.getChoices());
                yield choiceSchema;
            }
            default -> null;
        };
        if (schema != null) {
            builder.responseFormat(ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(writeJson(Map.copyOf(schema)))
                .build());
        }
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
