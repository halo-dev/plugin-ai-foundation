package run.halo.aifoundation.provider.protocol.messages;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.ProviderMetadataMaps;

/** Provider-owned policy for the reusable Anthropic Messages wire implementation. */
public interface AnthropicMessagesProfile {

    String providerType();

    String adapterType();

    default String endpointPath() {
        return "/v1/messages";
    }

    default void applyHeaders(HttpHeaders headers, ChatCompletionsOptions options) {
        if (options.getApiKey() != null && !options.getApiKey().isBlank()) {
            headers.set("x-api-key", options.getApiKey());
        }
        headers.set("anthropic-version", "2023-06-01");
    }

    default Map<String, Object> mediaContentPart(Media media,
        ChatCompletionsOptions options) {
        return null;
    }

    default void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
    }

    default Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        return ProviderMetadataMaps.immutableNonNull(metadata);
    }
}
