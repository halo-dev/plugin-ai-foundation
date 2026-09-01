package run.halo.aifoundation.provider.kimi;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.UriReferencePolicy;

/**
 * Kimi-native Chat Completions policy.
 *
 * <p>Model-specific reasoning and sampling rules belong to {@code AiModel} mappings. This profile
 * validates only the Kimi Chat protocol shape.
 */
final class KimiChatProfile implements ChatCompletionsProfile {

    private static final UriReferencePolicy MEDIA_REFERENCES =
        UriReferencePolicy.allowing("data:", "ms://");
    private static final int MAX_TOOLS = 128;
    private static final int MAX_STOP_SEQUENCES = 5;
    private static final int MAX_STOP_BYTES = 32;

    @Override
    public String providerType() {
        return "kimi";
    }

    @Override
    public String adapterType() {
        return "kimi-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        useMaxCompletionTokens(body);
        validateThinking(body.get("thinking"));
        validateStops(body.get("stop"));
        validateTools(body.get("tools"));
        applyPartial(body);
        rejectUndocumentedOptions(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mimeType = media.getMimeType();
        if (mimeType == null) {
            mimeType = MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }
        var mime = mimeType.toString().toLowerCase(Locale.ROOT);
        var field = mediaField(mime);
        if (field == null) {
            throw new IllegalArgumentException(
                "Kimi Chat supports only image and video media content, received: " + mime);
        }
        var reference = mediaReference(mime, media.getData());
        return Map.of("type", field, field, Map.of("url", reference));
    }

    private String mediaField(String mediaType) {
        if (mediaType.startsWith("image/")) {
            return "image_url";
        }
        if (mediaType.startsWith("video/")) {
            return "video_url";
        }
        return null;
    }

    private void useMaxCompletionTokens(Map<String, Object> body) {
        var deprecated = body.remove("max_tokens");
        if (deprecated != null && !body.containsKey("max_completion_tokens")) {
            body.put("max_completion_tokens", deprecated);
        }
    }

    private void validateThinking(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> thinking)) {
            throw new IllegalArgumentException("Kimi 'thinking' must be an object");
        }
        var type = thinking.get("type");
        if (type != null && !(type instanceof String)) {
            throw new IllegalArgumentException("Kimi thinking.type must be a string");
        }
        var keep = thinking.get("keep");
        if (keep != null && !(keep instanceof String)) {
            throw new IllegalArgumentException("Kimi thinking.keep must be a string");
        }
    }

    private void validateStops(Object value) {
        if (!(value instanceof List<?> stops)) {
            return;
        }
        if (stops.size() > MAX_STOP_SEQUENCES) {
            throw new IllegalArgumentException("Kimi accepts at most 5 stop sequences");
        }
        for (var stop : stops) {
            if (string(stop).getBytes(StandardCharsets.UTF_8).length > MAX_STOP_BYTES) {
                throw new IllegalArgumentException(
                    "Each Kimi stop sequence must be at most 32 UTF-8 bytes");
            }
        }
    }

    private void validateTools(Object value) {
        if (!(value instanceof List<?> tools)) {
            return;
        }
        if (tools.size() > MAX_TOOLS) {
            throw new IllegalArgumentException("Kimi accepts at most 128 tools");
        }
        var names = new HashSet<String>();
        for (var tool : tools) {
            if (!(tool instanceof Map<?, ?> toolMap)
                || !(toolMap.get("function") instanceof Map<?, ?> function)) {
                continue;
            }
            var name = string(function.get("name"));
            if (!name.isBlank() && !names.add(name)) {
                throw new IllegalArgumentException("Kimi tool function names must be unique: "
                    + name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPartial(Map<String, Object> body) {
        var partial = body.remove("partial");
        if (!Boolean.TRUE.equals(partial)) {
            return;
        }
        if (body.get("response_format") instanceof Map<?, ?> format
            && !"text".equals(format.get("type"))) {
            throw new IllegalArgumentException(
                "Kimi Partial Mode cannot be combined with structured response_format");
        }
        if (!(body.get("messages") instanceof List<?> messages)) {
            throw new IllegalArgumentException("Kimi Partial Mode requires a final message");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Kimi Partial Mode requires a final message");
        }
        var lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof Map<?, ?> lastRaw)) {
            throw new IllegalArgumentException("Kimi Partial Mode requires a final message");
        }
        var last = (Map<String, Object>) lastRaw;
        if (!"assistant".equals(last.get("role"))) {
            throw new IllegalArgumentException(
                "Kimi Partial Mode requires the final message to be an assistant text prefix");
        }
        if (last.containsKey("tool_calls")) {
            throw new IllegalArgumentException(
                "Kimi Partial Mode requires the final message to be an assistant text prefix");
        }
        last.put("partial", true);
    }

    private void rejectUndocumentedOptions(Map<String, Object> body) {
        for (var field : List.of("seed", "parallel_tool_calls", "top_k", "min_p",
            "repetition_penalty")) {
            if (body.containsKey(field)) {
                throw new IllegalArgumentException(
                    "Kimi Chat does not document request field: " + field);
            }
        }
    }

    private String mediaReference(String mimeType, Object data) {
        if (data instanceof byte[] bytes) {
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        }
        var value = data instanceof URI uri ? uri.toString() : string(data);
        if (MEDIA_REFERENCES.allows(value)) {
            return value;
        }
        throw new IllegalArgumentException(
            "Kimi media must be caller-provided data or an ms:// file reference; external URLs "
                + "are not accepted by the Kimi API");
    }

    private String string(Object value) {
        return value != null ? value.toString() : "";
    }
}
