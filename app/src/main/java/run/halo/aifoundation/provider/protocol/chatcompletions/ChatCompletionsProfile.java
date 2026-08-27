package run.halo.aifoundation.provider.protocol.chatcompletions;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.prompt.Prompt;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/**
 * Provider-owned policy hooks for the reusable Chat Completions wire implementation.
 *
 * <p>The common protocol supplies no endpoint, feature, option, or provider defaults. A provider
 * package must select them explicitly through this profile.
 */
public interface ChatCompletionsProfile {

    String providerType();

    String adapterType();

    default StreamDialect toolInputStreamDialect() {
        return new DeltaToolInputStreamDialect();
    }

    default void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
    }

    /**
     * Returns a provider-native content part for the supplied media, or {@code null} to use the
     * common Chat Completions mapping.
     *
     * <p>Providers should use this hook when their accepted media types or URL schemes are
     * narrower than the common protocol. The hook may reject unsupported media before any network
     * request is made.
     */
    default Map<String, Object> mediaContentPart(Media media) {
        return null;
    }

    default Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        return metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    default String reasoningContent(JsonNode message) {
        return null;
    }

    default Map<String, Object> additionalMessageMetadata(JsonNode message) {
        return Map.of();
    }

    default void customizeAssistantMessage(Map<String, Object> body,
        AssistantMessage message) {
    }
}
