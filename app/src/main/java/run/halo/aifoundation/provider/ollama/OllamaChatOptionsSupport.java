package run.halo.aifoundation.provider.ollama;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.schema.OutputType;

/** Maps provider-neutral requests onto Ollama's native chat options. */
final class OllamaChatOptionsSupport {

    static final String MODEL_NATIVE_OPTIONS_CONTEXT_KEY = "halo.ollama.model-native-options";
    static final String REQUEST_HEADERS_CONTEXT_KEY = "halo.ollama.request-headers";

    private OllamaChatOptionsSupport() {
    }

    static OllamaChatOptions basic(GenerateTextRequest request) {
        return builder(request).build();
    }

    static OllamaChatOptions tools(GenerateTextRequest request, List<ToolCallback> callbacks,
        Set<String> toolNames) {
        return builder(request).toolCallbacks(callbacks).build();
    }

    static OllamaChatOptions structured(GenerateTextRequest request) {
        var builder = builder(request);
        var output = request.getOutput();
        if (output == null) {
            return builder.build();
        }
        if (output.getType() == null) {
            return builder.build();
        }
        if (output.getType() == OutputType.TEXT) {
            return builder.build();
        }
        if (output.getType() == OutputType.JSON) {
            return builder.format("json").build();
        }
        var schema = switch (output.getType()) {
            case OBJECT -> output.getSchema();
            case ARRAY -> output.getElementSchema() == null ? null
                : Map.of("type", "array", "items", output.getElementSchema());
            case CHOICE -> output.getChoices() == null ? null
                : Map.of("type", "string", "enum", output.getChoices());
            default -> null;
        };
        return schema != null ? builder.format(schema).build() : builder.build();
    }

    static org.springframework.ai.chat.prompt.ChatOptions applyNativeOptions(
        org.springframework.ai.chat.prompt.ChatOptions options,
        Map<String, Object> nativeOptions) {
        if (!(options instanceof OllamaChatOptions ollamaOptions)) {
            return options;
        }
        if (nativeOptions == null) {
            return options;
        }
        if (nativeOptions.isEmpty()) {
            return options;
        }
        var context = new LinkedHashMap<String, Object>();
        if (ollamaOptions.getToolContext() != null) {
            context.putAll(ollamaOptions.getToolContext());
        }
        context.put(MODEL_NATIVE_OPTIONS_CONTEXT_KEY, Map.copyOf(nativeOptions));
        return ollamaOptions.mutate().toolContext(context).build();
    }

    private static OllamaChatOptions.Builder builder(GenerateTextRequest request) {
        var builder = OllamaChatOptions.builder()
            .temperature(request.getTemperature())
            .numPredict(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .minP(request.getMinP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .repeatPenalty(request.getRepetitionPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences());
        var context = new LinkedHashMap<String, Object>();
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            context.put(REQUEST_HEADERS_CONTEXT_KEY, Map.copyOf(request.getHeaders()));
        }
        if (!context.isEmpty()) {
            builder.toolContext(context);
        }
        return builder;
    }
}
