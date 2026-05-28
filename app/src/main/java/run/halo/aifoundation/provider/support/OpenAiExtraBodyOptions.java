package run.halo.aifoundation.provider.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.springframework.ai.openai.OpenAiChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Merges raw provider options with typed adapter-owned extra body values.
 */
public final class OpenAiExtraBodyOptions {

    private OpenAiExtraBodyOptions() {
    }

    public static void apply(OpenAiChatOptions.Builder builder, GenerateTextRequest request,
        String providerType, BiConsumer<Map<String, Object>, GenerateTextRequest> customizer) {
        var extraBody = new LinkedHashMap<String, Object>();
        var options = request.getProviderOptions() != null
            ? request.getProviderOptions().get(providerType)
            : null;
        if (options != null && !options.isEmpty()) {
            extraBody.putAll(options);
        }
        if (customizer != null) {
            customizer.accept(extraBody, request);
        }
        if (!extraBody.isEmpty()) {
            builder.extraBody(Map.copyOf(extraBody));
        }
    }
}
