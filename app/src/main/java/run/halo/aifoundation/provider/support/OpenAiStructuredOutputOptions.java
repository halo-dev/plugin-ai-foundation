package run.halo.aifoundation.provider.support;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.schema.OutputType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies Halo structured output hints to OpenAI-compatible chat options.
 */
public final class OpenAiStructuredOutputOptions {

    private OpenAiStructuredOutputOptions() {
    }

    public static void apply(OpenAiChatOptions.Builder builder, GenerateTextRequest request) {
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
                .jsonSchema(ResponseFormat.JsonSchema.builder()
                    .name(output.getName() != null ? output.getName() : "structured_output")
                    .schema(Map.copyOf(schema))
                    .strict(output.getStrict())
                    .build())
                .build());
        }
    }

    public static OpenAiChatOptions buildBasic(GenerateTextRequest request) {
        var builder = OpenAiChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stop(request.getStopSequences())
            .httpHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
        apply(builder, request);
        return builder.build();
    }
}
