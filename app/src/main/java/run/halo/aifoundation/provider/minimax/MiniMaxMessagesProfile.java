package run.halo.aifoundation.provider.minimax;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

final class MiniMaxMessagesProfile implements AnthropicMessagesProfile {

    @Override
    public String providerType() {
        return "minimax";
    }

    @Override
    public String adapterType() {
        return "minimax-messages";
    }

    @Override
    public String endpointPath() {
        return "/anthropic/v1/messages";
    }

    @Override
    public void applyHeaders(HttpHeaders headers, ChatCompletionsOptions options) {
        if (options.getApiKey() != null && !options.getApiKey().isBlank()) {
            headers.set("X-Api-Key", options.getApiKey());
        }
        headers.set("anthropic-version", "2023-06-01");
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media, ChatCompletionsOptions options) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("image/")) {
            return mediaBlock("image", media);
        }
        if (mime.startsWith("video/")) {
            return mediaBlock("video", media);
        }
        throw new IllegalArgumentException(
            "MiniMax Messages supports image and video media, received: " + mime);
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        validateSampling(body);
        body.putIfAbsent("max_tokens", 4096);
        applyActiveCache(body);
        removeIgnoredOptions(body);
    }

    private void removeIgnoredOptions(Map<String, Object> body) {
        for (var field : List.of("top_k", "stop_sequences", "mcp_servers",
            "context_management", "container")) {
            body.remove(field);
        }
    }

    static void validateSampling(Map<String, Object> body) {
        range(body.get("temperature"), "temperature", 0d, 2d);
        range(body.get("top_p"), "top_p", 0d, 1d);
    }

    private Map<String, Object> mediaBlock(String type, Media media) {
        return Map.of("type", type, "source",
            MediaContentSources.urlOrBase64Source(media, "MiniMax media"));
    }

    private void applyActiveCache(Map<String, Object> body) {
        var systemCache = body.remove("systemCacheControl");
        var lastMessageCache = body.remove("lastMessageCacheControl");
        var toolCache = body.remove("toolCacheControl");
        applySystemCache(body, systemCache);
        applyLastMessageCache(body, lastMessageCache);
        applyToolCache(body, toolCache);
    }

    private void applySystemCache(Map<String, Object> body, Object cache) {
        if (cache == null) {
            return;
        }
        if (!(body.get("system") instanceof String system)) {
            return;
        }
        body.put("system", List.of(textBlock(system, cache)));
    }

    private void applyLastMessageCache(Map<String, Object> body, Object cache) {
        var message = lastMap(body.get("messages"));
        if (cache == null || message == null) {
            return;
        }
        message.put("content", withCache(message.get("content"), cache));
    }

    private void applyToolCache(Map<String, Object> body, Object cache) {
        var tool = lastMap(body.get("tools"));
        if (cache == null || tool == null) {
            return;
        }
        tool.put("cache_control", cache);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastMap(Object value) {
        if (!(value instanceof List<?> values)) {
            return null;
        }
        if (values.isEmpty()) {
            return null;
        }
        var last = values.get(values.size() - 1);
        return last instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private Object withCache(Object content, Object cache) {
        if (content instanceof String text) {
            return List.of(textBlock(text, cache));
        }
        if (content instanceof List<?> blocks && !blocks.isEmpty()) {
            var values = blocks.stream().map(block -> block instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<?, ?>) map) : block).toList();
            if (values.get(values.size() - 1) instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                var last = (Map<String, Object>) raw;
                last.put("cache_control", cache);
            }
            return values;
        }
        return content;
    }

    private Map<String, Object> textBlock(String text, Object cache) {
        var value = new LinkedHashMap<String, Object>();
        value.put("type", "text");
        value.put("text", text);
        value.put("cache_control", cache);
        return value;
    }

    private static void range(Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                "MiniMax " + field + " must be a number");
        }
        var numericValue = number.doubleValue();
        if (numericValue >= minimum && numericValue <= maximum) {
            return;
        }
        throw new IllegalArgumentException(
            "MiniMax " + field + " must be between " + minimum + " and " + maximum);
    }
}
