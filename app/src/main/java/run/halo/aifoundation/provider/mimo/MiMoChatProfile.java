package run.halo.aifoundation.provider.mimo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.UriReferencePolicy;

/** MiMo Chat policy for full-modal input, thinking, Web Search, and citations. */
final class MiMoChatProfile implements ChatCompletionsProfile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UriReferencePolicy MEDIA_REFERENCES =
        UriReferencePolicy.allowing("http://", "https://", "data:");
    private static final Set<String> EXTRA_FIELDS = Set.of("thinking", "builtinTools", "video");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
        "video/mp4", "video/quicktime", "video/x-msvideo", "video/x-ms-wmv");

    @Override
    public String providerType() {
        return "mimo";
    }

    @Override
    public String adapterType() {
        return "mimo-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        validateExtraFields(options.getExtraBody());
        useMaxCompletionTokens(body);
        applyBuiltinTools(body);
        applyVideoOptions(body);
        validateThinking(body.get("thinking"));
        validateLimits(body);
        normalizeToolChoice(body);
        validateTools(body.get("tools"));
        validateStructuredOutput(body.get("response_format"));
        normalizeThinkingSampling(body);
        rejectUndocumentedFields(body);
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
        if (mime.startsWith("audio/")) {
            return Map.of("type", "input_audio", "input_audio", Map.of("data", reference));
        }
        if (VIDEO_MIME_TYPES.contains(mime)) {
            return new LinkedHashMap<>(Map.of("type", "video_url", "video_url",
                Map.of("url", reference)));
        }
        throw new IllegalArgumentException(
            "MiMo Chat supports image, audio, MP4, MOV, AVI, or WMV media; received: " + mime);
    }

    @Override
    public String reasoningContent(JsonNode message) {
        var value = message.path("reasoning_content");
        return value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
    }

    @Override
    public Map<String, Object> additionalMessageMetadata(JsonNode message) {
        var metadata = new LinkedHashMap<String, Object>();
        if (message.path("annotations").isArray()) {
            metadata.put("annotations", OBJECT_MAPPER.convertValue(message.path("annotations"),
                Object.class));
        }
        if (message.path("error_message").isTextual()) {
            metadata.put("errorMessage", message.path("error_message").asText());
        }
        return Map.copyOf(metadata);
    }

    private void validateExtraFields(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(EXTRA_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported MiMo Chat option(s): "
                + String.join(", ", unknown));
        }
    }

    private void useMaxCompletionTokens(Map<String, Object> body) {
        var deprecated = body.remove("max_tokens");
        if (deprecated != null && !body.containsKey("max_completion_tokens")) {
            body.put("max_completion_tokens", deprecated);
        }
    }

    private void applyBuiltinTools(Map<String, Object> body) {
        var value = body.remove("builtinTools");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> builtins)) {
            throw new IllegalArgumentException("MiMo builtinTools must be an array");
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> existing) {
            tools.addAll(existing);
        }
        for (var item : builtins) {
            validateWebSearchTool(item);
            tools.add(item);
        }
        body.put("tools", List.copyOf(tools));
    }

    @SuppressWarnings("unchecked")
    private void applyVideoOptions(Map<String, Object> body) {
        var value = body.remove("video");
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("MiMo video option must be an object");
        }
        var options = (Map<String, Object>) raw;
        var unknown = new LinkedHashSet<>(options.keySet());
        unknown.removeAll(Set.of("fps", "media_resolution"));
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported MiMo video option(s): "
                + String.join(", ", unknown));
        }
        var fps = decimal(options.get("fps"), "video.fps");
        if (fps != null && (fps < 0.1d || fps > 10d)) {
            throw new IllegalArgumentException("MiMo video.fps must be between 0.1 and 10");
        }
        var resolution = text(options.get("media_resolution"));
        if (!isSupportedResolution(resolution)) {
            throw new IllegalArgumentException(
                "MiMo video.media_resolution must be 'default' or 'max'");
        }
        var videoParts = videoParts(body.get("messages"));
        if (videoParts.isEmpty()) {
            throw new IllegalArgumentException(
                "MiMo video options require at least one video input");
        }
        for (var part : videoParts) {
            if (fps != null) {
                part.put("fps", fps);
            }
            if (resolution != null) {
                part.put("media_resolution", resolution);
            }
        }
    }

    private boolean isSupportedResolution(String resolution) {
        if (resolution == null) {
            return true;
        }
        if ("default".equals(resolution)) {
            return true;
        }
        return "max".equals(resolution);
    }

    private List<Map<String, Object>> videoParts(Object value) {
        if (!(value instanceof List<?> messages)) {
            return List.of();
        }
        var videoParts = new java.util.ArrayList<Map<String, Object>>();
        for (var message : messages) {
            videoParts.addAll(messageVideoParts(message));
        }
        return List.copyOf(videoParts);
    }

    private List<Map<String, Object>> messageVideoParts(Object value) {
        var message = map(value);
        if (message == null || !(message.get("content") instanceof List<?> content)) {
            return List.of();
        }
        return content.stream()
            .map(this::map)
            .filter(java.util.Objects::nonNull)
            .filter(part -> "video_url".equals(part.get("type")))
            .toList();
    }

    private void validateThinking(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> thinking) || thinking.size() != 1) {
            throw new IllegalArgumentException(
                "MiMo thinking must be an object containing only 'type'");
        }
        var type = text(thinking.get("type"));
        if (!"enabled".equals(type) && !"disabled".equals(type)) {
            throw new IllegalArgumentException(
                "MiMo thinking.type must be 'enabled' or 'disabled'");
        }
    }

    private void validateLimits(Map<String, Object> body) {
        var max = integer(body.get("max_completion_tokens"), "max_completion_tokens");
        if (max != null && (max < 1 || max > 131072)) {
            throw new IllegalArgumentException(
                "MiMo max_completion_tokens must be between 1 and 131072");
        }
        range(body.get("temperature"), "temperature", 0d, 1.5d);
        range(body.get("top_p"), "top_p", 0.01d, 1d);
        range(body.get("presence_penalty"), "presence_penalty", -2d, 2d);
        range(body.get("frequency_penalty"), "frequency_penalty", -2d, 2d);
        if (body.get("stop") instanceof List<?> stops && stops.size() > 4) {
            throw new IllegalArgumentException("MiMo accepts at most 4 stop sequences");
        }
    }

    private void normalizeToolChoice(Map<String, Object> body) {
        var value = body.get("tool_choice");
        if (value == null) {
            return;
        }
        if ("auto".equals(value)) {
            return;
        }
        body.remove("tool_choice");
    }

    private void validateTools(Object value) {
        if (!(value instanceof List<?> tools)) {
            return;
        }
        for (var item : tools) {
            var tool = map(item);
            var type = tool != null ? text(tool.get("type")) : null;
            if (!"function".equals(type) && !"web_search".equals(type)) {
                throw new IllegalArgumentException(
                    "MiMo Chat supports function and web_search tools only");
            }
        }
    }

    private void validateWebSearchTool(Object value) {
        var tool = map(value);
        if (tool == null || !"web_search".equals(tool.get("type"))) {
            throw new IllegalArgumentException(
                "MiMo builtinTools entries must have type='web_search'");
        }
        var unknown = new LinkedHashSet<>(tool.keySet());
        unknown.removeAll(Set.of("type", "max_keyword", "force_search", "limit",
            "user_location"));
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported MiMo web_search field(s): "
                + String.join(", ", unknown));
        }
        positiveInteger(tool.get("max_keyword"), "web_search.max_keyword");
        positiveInteger(tool.get("limit"), "web_search.limit");
        if (tool.get("force_search") != null && !(tool.get("force_search") instanceof Boolean)) {
            throw new IllegalArgumentException("MiMo web_search.force_search must be boolean");
        }
        if (tool.get("user_location") != null
            && !(tool.get("user_location") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                "MiMo web_search.user_location must be an object");
        }
    }

    private void validateStructuredOutput(Object value) {
        if (!(value instanceof Map<?, ?> format)) {
            return;
        }
        var type = text(format.get("type"));
        if (!"text".equals(type) && !"json_object".equals(type)) {
            throw new IllegalArgumentException(
                "MiMo Chat supports text or JSON object output, not " + type);
        }
    }

    private void normalizeThinkingSampling(Map<String, Object> body) {
        if (isThinkingDisabled(body.get("thinking"))) {
            return;
        }
        body.remove("temperature");
        body.remove("top_p");
    }

    private boolean isThinkingDisabled(Object value) {
        var thinking = map(value);
        if (thinking == null) {
            return false;
        }
        return "disabled".equals(thinking.get("type"));
    }

    private void rejectUndocumentedFields(Map<String, Object> body) {
        for (var field : List.of("seed", "parallel_tool_calls", "logprobs", "top_logprobs",
            "verbosity", "store", "metadata", "service_tier")) {
            if (body.containsKey(field)) {
                throw new IllegalArgumentException(
                    "MiMo Chat does not document request field: " + field);
            }
        }
    }

    private String mediaReference(Media media, String mime) {
        var data = media.getData();
        if (data instanceof URI uri) {
            return validateMediaReference(uri.toString());
        }
        if (data instanceof String value && !value.isBlank()) {
            return validateMediaReference(value);
        }
        if (data instanceof byte[] bytes) {
            return dataUrl(mime, bytes);
        }
        if (data instanceof Resource resource) {
            try {
                return dataUrl(mime, resource.getContentAsByteArray());
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Failed to read MiMo media input", e);
            }
        }
        throw new IllegalArgumentException(
            "MiMo media content must be a URL or binary data");
    }

    private String validateMediaReference(String value) {
        if (MEDIA_REFERENCES.allows(value)) {
            return value;
        }
        throw new IllegalArgumentException(
            "MiMo media references must use HTTP(S) or a data URL");
    }

    private String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private void positiveInteger(Object value, String field) {
        var number = integer(value, field);
        if (number != null && number < 1) {
            throw new IllegalArgumentException("MiMo " + field + " must be positive");
        }
    }

    private Integer integer(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number && Math.rint(number.doubleValue()) == number.doubleValue()) {
            return number.intValue();
        }
        throw new IllegalArgumentException("MiMo " + field + " must be an integer");
    }

    private Double decimal(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("MiMo " + field + " must be numeric");
    }

    private void range(Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.doubleValue() < minimum
            || number.doubleValue() > maximum) {
            throw new IllegalArgumentException("MiMo " + field + " must be between " + minimum
                + " and " + maximum);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String text(Object value) {
        return value != null && !value.toString().isBlank() ? value.toString() : null;
    }

}
