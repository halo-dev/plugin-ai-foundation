package run.halo.aifoundation.provider.minimax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;
import run.halo.aifoundation.provider.support.ReasoningProviderMetadata;

/** MiniMax policy for its optional OpenAI-compatible Chat Completions surface. */
final class MiniMaxChatProfile implements ChatCompletionsProfile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String providerType() {
        return "minimax";
    }

    @Override
    public String adapterType() {
        return "minimax-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        var maxTokens = body.remove("max_tokens");
        if (maxTokens != null && !body.containsKey("max_completion_tokens")) {
            body.put("max_completion_tokens", maxTokens);
        }
        MiniMaxMessagesProfile.validateSampling(body);
        body.putIfAbsent("reasoning_split", true);
        normalizeIgnoredOptions(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("image/")) {
            return Map.of("type", "image_url", "image_url", Map.of(
                "url", MediaContentSources.urlOrDataUrl(media, "MiniMax image")));
        }
        if (mime.startsWith("video/")) {
            return Map.of("type", "video_url", "video_url", Map.of(
                "url", MediaContentSources.urlOrDataUrl(media, "MiniMax video")));
        }
        throw new IllegalArgumentException(
            "MiniMax Chat supports image and video media, received: " + mime);
    }

    @Override
    public String reasoningContent(JsonNode message) {
        var details = message.path("reasoning_details");
        if (!details.isArray()) {
            return null;
        }
        var text = new StringBuilder();
        for (var detail : details) {
            for (var field : List.of("text", "thinking", "content")) {
                if (detail.path(field).isTextual()) {
                    text.append(detail.path(field).asText());
                    break;
                }
            }
        }
        return text.isEmpty() ? null : text.toString();
    }

    @Override
    public Map<String, Object> additionalMessageMetadata(JsonNode message) {
        var details = message.path("reasoning_details");
        return details.isArray()
            ? Map.of("reasoningDetails", OBJECT_MAPPER.convertValue(details, Object.class))
            : Map.of();
    }

    @Override
    public void customizeAssistantMessage(Map<String, Object> body, AssistantMessage message) {
        var metadata = message.getMetadata();
        var details = providerReasoningDetails(metadata);
        if (details != null) {
            body.remove("reasoning_content");
            body.put("reasoning_details", details);
            return;
        }
        var reasoning = metadata != null ? metadata.get("reasoningContent") : null;
        if (reasoning != null && !reasoning.toString().isBlank()) {
            body.remove("reasoning_content");
            body.put("reasoning_details", List.of(Map.of(
                "type", "reasoning.text", "text", reasoning.toString())));
        }
    }

    private Object providerReasoningDetails(Map<String, Object> metadata) {
        return ReasoningProviderMetadata.values(metadata, "minimax").get("reasoningDetails");
    }

    private void normalizeIgnoredOptions(Map<String, Object> body) {
        for (var field : List.of("presence_penalty", "frequency_penalty", "logit_bias")) {
            body.remove(field);
        }
    }

}
