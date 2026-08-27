package run.halo.aifoundation.provider.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.ReasoningProviderMetadata;

/** OpenRouter routing, multimodal, reasoning-continuation, and metadata policy. */
final class OpenRouterChatProfile implements ChatCompletionsProfile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SIGNED_REASONING_FORMATS = Set.of(
        "anthropic-claude-v1", "google-gemini-v1");

    @Override
    public String providerType() {
        return "openrouter";
    }

    @Override
    public String adapterType() {
        return "openrouter-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        rejectResponsesServerTools(body);
        var maxTokens = body.remove("max_tokens");
        if (maxTokens != null && !body.containsKey("max_completion_tokens")) {
            body.put("max_completion_tokens", maxTokens);
        }
        body.putIfAbsent("usage", Map.of("include", true));
        OpenRouterRoutingOptions.validate(body.get("provider"), "chat");
        deduplicateReasoningDetails(body.get("messages"));
    }

    private void rejectResponsesServerTools(Map<String, Object> body) {
        if (!body.containsKey("serverTools")) {
            return;
        }
        throw new IllegalArgumentException(
            "OpenRouter serverTools require the openrouter-responses adapter");
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = media.getMimeType() != null
            ? media.getMimeType().toString().toLowerCase(Locale.ROOT)
            : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        var reference = mediaReference(media, mime);
        if (mime.startsWith("image/")) {
            return Map.of("type", "image_url", "image_url", Map.of("url", reference));
        }
        if (mime.startsWith("video/")) {
            return Map.of("type", "video_url", "video_url", Map.of("url", reference));
        }
        if (mime.startsWith("audio/")) {
            return Map.of("type", "input_audio", "input_audio", Map.of(
                "data", rawBase64(media), "format", audioFormat(mime)));
        }
        return Map.of("type", "file", "file", Map.of(
            "filename", media.getName() != null ? media.getName() : "",
            "file_data", reference));
    }

    @Override
    public Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        return metadata == null || metadata.isEmpty()
            ? Map.of() : Map.of("openrouter", Map.copyOf(metadata));
    }

    @Override
    public String reasoningContent(JsonNode message) {
        var direct = message.path("reasoning");
        if (direct.isTextual() && !direct.asText().isEmpty()) {
            return direct.asText();
        }
        var text = new StringBuilder();
        var details = message.path("reasoning_details");
        if (details.isArray()) {
            for (var detail : details) {
                if (detail.path("text").isTextual()) {
                    text.append(detail.path("text").asText());
                } else if (detail.path("summary").isTextual()) {
                    text.append(detail.path("summary").asText());
                }
            }
        }
        return text.isEmpty() ? null : text.toString();
    }

    @Override
    public Map<String, Object> additionalMessageMetadata(JsonNode message) {
        var metadata = new LinkedHashMap<String, Object>();
        var continuation = new LinkedHashMap<String, Object>();
        var details = message.path("reasoning_details");
        if (details.isArray()) {
            continuation.put("reasoningDetails", OBJECT_MAPPER.convertValue(details, Object.class));
        }
        var annotations = message.path("annotations");
        if (annotations.isArray()) {
            var value = OBJECT_MAPPER.convertValue(annotations, Object.class);
            continuation.put("annotations", value);
            metadata.put("annotations", value);
        }
        var images = message.path("images");
        if (images.isArray()) {
            metadata.put("images", OBJECT_MAPPER.convertValue(images, Object.class));
        }
        if (!continuation.isEmpty()) {
            metadata.put("openRouterReasoningMetadata", Map.copyOf(continuation));
        }
        return Map.copyOf(metadata);
    }

    @Override
    public void customizeAssistantMessage(Map<String, Object> body, AssistantMessage message) {
        var continuation = continuationMetadata(message.getMetadata());
        if (continuation == null) {
            body.remove("reasoning_content");
            return;
        }
        body.remove("reasoning_content");
        if (continuation.containsKey("reasoningDetails")) {
            body.put("reasoning_details", continuation.get("reasoningDetails"));
        }
        if (continuation.containsKey("annotations")) {
            body.put("annotations", continuation.get("annotations"));
        }
        var reasoning = message.getMetadata() != null
            ? message.getMetadata().get("reasoningContent") : null;
        if (reasoning == null) {
            return;
        }
        if (!(continuation.get("reasoningDetails") instanceof List<?> details)) {
            return;
        }
        if (details.isEmpty()) {
            return;
        }
        body.put("reasoning", reasoning.toString());
    }

    private Map<?, ?> continuationMetadata(Map<String, Object> metadata) {
        var value = ReasoningProviderMetadata.values(metadata, "openrouter")
            .get("openRouterReasoningMetadata");
        return value instanceof Map<?, ?> continuation ? continuation : null;
    }

    @SuppressWarnings("unchecked")
    private void deduplicateReasoningDetails(Object value) {
        if (!(value instanceof List<?> messages)) {
            return;
        }
        var seen = new HashSet<String>();
        for (var item : messages) {
            if (!(item instanceof Map<?, ?> rawMessage)
                || !(rawMessage.get("reasoning_details") instanceof List<?> rawDetails)) {
                continue;
            }
            var message = (Map<String, Object>) rawMessage;
            var details = new ArrayList<Object>();
            for (var rawDetail : rawDetails) {
                if (!(rawDetail instanceof Map<?, ?> detail) || !validSignedDetail(detail)) {
                    continue;
                }
                var key = reasoningDetailKey(detail);
                if (key != null && seen.add(key)) {
                    details.add(rawDetail);
                }
            }
            message.put("reasoning_details", List.copyOf(details));
            if (details.isEmpty()) {
                message.remove("reasoning");
            }
        }
    }

    private boolean validSignedDetail(Map<?, ?> detail) {
        if (!"reasoning.text".equals(detail.get("type"))) {
            return true;
        }
        var format = detail.get("format") != null
            ? detail.get("format").toString() : "anthropic-claude-v1";
        return !SIGNED_REASONING_FORMATS.contains(format)
            || detail.get("signature") != null && !detail.get("signature").toString().isBlank();
    }

    private String reasoningDetailKey(Map<?, ?> detail) {
        for (var field : List.of("id", "data", "signature")) {
            var value = detail.get(field);
            if (value != null && !value.toString().isBlank()) {
                return field + ":" + value;
            }
        }
        return null;
    }

    private String mediaReference(Media media, String mime) {
        var data = media.getData();
        if (data instanceof URI uri) {
            return uri.toString();
        }
        if (data instanceof String text && !text.isBlank()) {
            return text;
        }
        return "data:" + mime + ";base64," + rawBase64(media);
    }

    private String rawBase64(Media media) {
        var data = media.getData();
        if (data instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (data instanceof Resource resource) {
            try {
                return Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Failed to read OpenRouter media content", e);
            }
        }
        throw new IllegalArgumentException("OpenRouter media content must be a URL or bytes");
    }

    private String audioFormat(String mime) {
        var subtype = mime.substring(mime.indexOf('/') + 1).split("[;+]", 2)[0];
        return switch (subtype) {
            case "mpeg" -> "mp3";
            case "x-wav" -> "wav";
            default -> subtype;
        };
    }
}
