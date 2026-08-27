package run.halo.aifoundation.provider.protocol.responses;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Provider-owned policy for a Responses-compatible wire protocol. */
public interface ResponsesProfile {

    String providerType();

    String adapterType();

    default String endpointPath() {
        return "/responses";
    }

    default void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
    }

    /** Returns a provider-native media input part, or {@code null} for common mapping. */
    default Map<String, Object> mediaContentPart(Media media) {
        return null;
    }

    /**
     * Returns provider-native input items that must precede the reconstructed assistant message.
     *
     * <p>This hook is intentionally narrow: Responses providers whose reasoning protocol requires
     * verbatim replay can retain the original sanitized output item without teaching the common
     * codec a provider-specific reasoning schema.
     */
    default List<Map<String, Object>> assistantInputItems(AssistantMessage message) {
        return ResponsesOutputReplay.reasoningInputItems(message, providerType());
    }

    default String eventType(JsonNode event) {
        return event.path("type").asText("");
    }

    default Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        return metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** Normalize a provider-specific output item while retaining its sanitized original shape. */
    default ResponsesProviderOutput providerOutputItem(JsonNode item) {
        return ResponsesProviderOutput.preserved();
    }

    default boolean preserveUnknownEvents() {
        return true;
    }
}
