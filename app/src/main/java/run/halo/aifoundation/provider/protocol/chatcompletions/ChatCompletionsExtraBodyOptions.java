package run.halo.aifoundation.provider.protocol.chatcompletions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

/**
 * Applies adapter-owned extra body values.
 */
public final class ChatCompletionsExtraBodyOptions {

    private ChatCompletionsExtraBodyOptions() {
    }

    public static void apply(ChatCompletionsOptions.Builder builder, GenerateTextRequest request,
        String providerType, BiConsumer<Map<String, Object>, GenerateTextRequest> customizer) {
        var extraBody = new LinkedHashMap<String, Object>();
        var providerOptions = ProviderRequestOptions.get(
            request.getProviderOptions(), providerType);
        if (providerOptions != null) {
            extraBody.putAll(providerOptions);
        }
        if (customizer != null) {
            customizer.accept(extraBody, request);
        }
        if (!extraBody.isEmpty()) {
            builder.extraBody(Map.copyOf(extraBody));
        }
    }

}
