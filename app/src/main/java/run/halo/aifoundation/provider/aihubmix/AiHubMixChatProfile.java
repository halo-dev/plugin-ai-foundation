package run.halo.aifoundation.provider.aihubmix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.ProviderMetadataMaps;
import run.halo.aifoundation.provider.support.ReasoningProviderMetadata;

/** AIHubMix Chat-specific media, reasoning-detail, annotation, and usage policy. */
final class AiHubMixChatProfile implements ChatCompletionsProfile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String providerType() {
        return "aihubmix";
    }

    @Override
    public String adapterType() {
        return "aihubmix-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        if (body.remove("builtinTools") != null) {
            throw new IllegalArgumentException(
                "AIHubMix builtinTools are available only through the Responses adapter");
        }
        var maxTokens = body.remove("max_tokens");
        if (maxTokens != null && !body.containsKey("max_completion_tokens")) {
            body.put("max_completion_tokens", maxTokens);
        }
        if (stream) {
            body.putIfAbsent("stream_options", Map.of("include_usage", true));
        }
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = media.getMimeType() != null
            ? media.getMimeType().toString().toLowerCase(Locale.ROOT)
            : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        if (mime.startsWith("image/")) {
            return Map.of("type", "image_url", "image_url", Map.of(
                "url", mediaReference(media, mime)));
        }
        if (mime.startsWith("audio/")) {
            return Map.of("type", "input_audio", "input_audio", Map.of(
                "data", rawBase64(media), "format", audioFormat(mime)));
        }
        throw new IllegalArgumentException(
            "AIHubMix Chat supports image and audio media only; use Responses for files");
    }

    @Override
    public Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        return ProviderMetadataMaps.namespaced("aihubmix", metadata);
    }

    @Override
    public String reasoningContent(JsonNode message) {
        for (var field : List.of("reasoning_content", "reasoning")) {
            var value = message.path(field);
            if (value.isTextual() && !value.asText().isEmpty()) {
                return value.asText();
            }
        }
        var result = new StringBuilder();
        for (var detail : message.path("reasoning_details")) {
            for (var field : List.of("text", "summary")) {
                if (detail.path(field).isTextual()) {
                    result.append(detail.path(field).asText());
                    break;
                }
            }
        }
        return result.isEmpty() ? null : result.toString();
    }

    @Override
    public Map<String, Object> additionalMessageMetadata(JsonNode message) {
        var continuation = new LinkedHashMap<String, Object>();
        for (var field : List.of("reasoning_details", "annotations")) {
            var value = message.path(field);
            if (value.isArray()) {
                continuation.put(field, OBJECT_MAPPER.convertValue(value, Object.class));
            }
        }
        return continuation.isEmpty() ? Map.of()
            : Map.of("aiHubMixContinuation", Map.copyOf(continuation));
    }

    @Override
    public void customizeAssistantMessage(Map<String, Object> body, AssistantMessage message) {
        body.remove("reasoning_content");
        var continuation = continuationMetadata(message.getMetadata());
        if (continuation == null) {
            return;
        }
        continuation.forEach((key, value) -> body.put(key.toString(), value));
    }

    private Map<?, ?> continuationMetadata(Map<String, Object> metadata) {
        var value = ReasoningProviderMetadata.values(metadata, "aihubmix")
            .get("aiHubMixContinuation");
        return value instanceof Map<?, ?> continuation ? continuation : null;
    }

    private String mediaReference(Media media, String mime) {
        if (media.getData() instanceof URI uri) {
            return uri.toString();
        }
        if (media.getData() instanceof String value && !value.isBlank()) {
            return value;
        }
        return "data:" + mime + ";base64," + rawBase64(media);
    }

    private String rawBase64(Media media) {
        if (media.getData() instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (media.getData() instanceof Resource resource) {
            try {
                return Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Failed to read AIHubMix media", e);
            }
        }
        throw new IllegalArgumentException("AIHubMix audio must contain bytes");
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
