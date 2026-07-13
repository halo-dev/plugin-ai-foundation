package run.halo.aifoundation.provider.support.openai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Applies adapter-owned extra body values.
 */
public final class OpenAiExtraBodyOptions {

    private OpenAiExtraBodyOptions() {
    }

    public static void apply(OpenAiCompatibleChatOptions.Builder builder, GenerateTextRequest request,
        String providerType, BiConsumer<Map<String, Object>, GenerateTextRequest> customizer) {
        var extraBody = new LinkedHashMap<String, Object>();
        if (customizer != null) {
            customizer.accept(extraBody, request);
        }
        if (!extraBody.isEmpty()) {
            builder.extraBody(Map.copyOf(extraBody));
        }
    }
}
