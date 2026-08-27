package run.halo.aifoundation.provider.zhipu;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Current BigModel Chat contract: thinking, streaming tools, built-ins, and multimodal input. */
final class ZhiPuChatProfile implements ChatCompletionsProfile {

    private static final UriReferencePolicy HTTP_MEDIA_REFERENCES =
        UriReferencePolicy.allowing("http://", "https://");
    private static final UriReferencePolicy DATA_REFERENCES =
        UriReferencePolicy.allowing("data:");
    private static final Set<String> EXTRA_FIELDS = Set.of(
        "thinking", "reasoning_effort", "do_sample", "tool_stream", "builtinTools",
        "request_id", "user_id");
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
        "application/pdf", "text/plain", "application/json", "application/jsonl",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
        "video/mp4", "video/x-matroska", "video/quicktime");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final Map<String, String> AUDIO_FORMATS = Map.of(
        "audio/wav", "wav",
        "audio/x-wav", "wav",
        "audio/mpeg", "mp3",
        "audio/mp3", "mp3");
    private static final Set<String> REASONING_EFFORTS = Set.of(
        "max", "xhigh", "high", "medium", "low", "minimal", "none");

    @Override
    public String providerType() {
        return "zhipuai";
    }

    @Override
    public String adapterType() {
        return "zhipu-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        validateExtraFields(options.getExtraBody());
        body.remove("stream_options");
        applyBuiltinTools(body);
        validateThinking(body);
        validateSamplingAndLimits(body);
        validateTools(body);
        validateIdentifiers(body);
        validateStructuredOutput(body);
        validateMedia(body);
        rejectUndocumentedOpenAiFields(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = media.getMimeType() != null
            ? media.getMimeType().toString().toLowerCase(Locale.ROOT)
            : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        if (IMAGE_MIME_TYPES.contains(mime)) {
            return Map.of("type", "image_url", "image_url", Map.of(
                "url", mediaReference(media, mime, true)));
        }
        if (VIDEO_MIME_TYPES.contains(mime)) {
            return Map.of("type", "video_url", "video_url", Map.of(
                "url", mediaReference(media, mime, false)));
        }
        if (DOCUMENT_MIME_TYPES.contains(mime)) {
            return Map.of("type", "file_url", "file_url", Map.of(
                "url", mediaReference(media, mime, false)));
        }
        var audioFormat = AUDIO_FORMATS.get(mime);
        if (audioFormat != null) {
            return Map.of("type", "input_audio", "input_audio", Map.of(
                "data", rawBase64(media), "format", audioFormat));
        }
        throw new IllegalArgumentException(
            "Zhipu Chat supports JPEG/PNG images, MP4/MKV/MOV video, documented files, "
                + "or WAV/MP3 audio; received: " + mime);
    }

    @Override
    public String reasoningContent(JsonNode message) {
        var value = message.path("reasoning_content");
        return value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
    }

    @Override
    public Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var normalized = new LinkedHashMap<>(metadata);
        if (metadata.get("web_search") != null) {
            normalized.put("sources", metadata.get("web_search"));
        }
        return Map.copyOf(normalized);
    }

    private void validateExtraFields(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(EXTRA_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Zhipu Chat option(s): "
                + String.join(", ", unknown));
        }
    }

    private void applyBuiltinTools(Map<String, Object> body) {
        var value = body.remove("builtinTools");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> builtins)) {
            throw new IllegalArgumentException("Zhipu builtinTools must be an array");
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> existing) {
            tools.addAll(existing);
        }
        for (var tool : builtins) {
            validateBuiltinTool(tool);
            tools.add(tool);
        }
        if (tools.size() > 128) {
            throw new IllegalArgumentException("Zhipu accepts at most 128 tools");
        }
        body.put("tools", List.copyOf(tools));
    }

    private void validateThinking(Map<String, Object> body) {
        var value = body.get("thinking");
        if (value != null) {
            if (!(value instanceof Map<?, ?> thinking)) {
                throw new IllegalArgumentException("Zhipu thinking must be an object");
            }
            var unknown = new LinkedHashSet<>(thinking.keySet());
            unknown.removeAll(Set.of("type", "clear_thinking"));
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Unsupported Zhipu thinking field(s): "
                    + join(unknown));
            }
            var type = text(thinking.get("type"));
            if (type != null && !Set.of("enabled", "disabled").contains(type)) {
                throw new IllegalArgumentException(
                    "Zhipu thinking.type must be 'enabled' or 'disabled'");
            }
            if (thinking.get("clear_thinking") != null
                && !(thinking.get("clear_thinking") instanceof Boolean)) {
                throw new IllegalArgumentException("Zhipu thinking.clear_thinking must be boolean");
            }
        }
        var effort = text(body.get("reasoning_effort"));
        if (effort == null) {
            return;
        }
        if (!REASONING_EFFORTS.contains(effort)) {
            throw new IllegalArgumentException("Unsupported Zhipu reasoning_effort: " + effort);
        }
    }

    private void validateSamplingAndLimits(Map<String, Object> body) {
        booleanValue(body.get("do_sample"), "do_sample");
        booleanValue(body.get("tool_stream"), "tool_stream");
        decimalRange(body.get("temperature"), "temperature", 0d, 1d);
        decimalRange(body.get("top_p"), "top_p", 0.01d, 1d);
        integerRange(body.get("max_tokens"), "max_tokens", 1, 131072);
        if (body.get("stop") instanceof List<?> stops && stops.size() > 4) {
            throw new IllegalArgumentException("Zhipu accepts at most 4 stop sequences");
        }
    }

    private void validateTools(Map<String, Object> body) {
        var choice = body.get("tool_choice");
        if (choice != null && !"auto".equals(choice)) {
            throw new IllegalArgumentException("Zhipu supports only tool_choice='auto'");
        }
        if (!(body.get("tools") instanceof List<?> tools)) {
            return;
        }
        if (tools.size() > 128) {
            throw new IllegalArgumentException("Zhipu accepts at most 128 tools");
        }
        for (var value : tools) {
            var tool = map(value);
            var type = tool != null ? text(tool.get("type")) : null;
            if (!Set.of("function", "retrieval", "web_search", "mcp").contains(type)) {
                throw new IllegalArgumentException(
                    "Zhipu tools must be function, retrieval, web_search, or mcp");
            }
            if ("function".equals(type)) {
                var function = map(tool.get("function"));
                if (function == null || function.get("strict") != null) {
                    throw new IllegalArgumentException(
                        "Zhipu function tools do not document the OpenAI strict field");
                }
            }
        }
    }

    private void validateBuiltinTool(Object value) {
        var tool = map(value);
        var type = tool != null ? text(tool.get("type")) : null;
        if (!Set.of("retrieval", "web_search", "mcp").contains(type)) {
            throw new IllegalArgumentException(
                "Zhipu builtinTools entries must be retrieval, web_search, or mcp");
        }
        var unknown = new LinkedHashSet<>(tool.keySet());
        unknown.removeAll(Set.of("type", type));
        if (!unknown.isEmpty() || map(tool.get(type)) == null) {
            throw new IllegalArgumentException(
                "Zhipu " + type + " tool must contain only its matching configuration object");
        }
        switch (type) {
            case "web_search" -> validateWebSearch(map(tool.get(type)));
            case "retrieval" -> validateRetrieval(map(tool.get(type)));
            case "mcp" -> validateMcp(map(tool.get(type)));
            default -> throw new IllegalStateException("Unexpected Zhipu built-in tool: " + type);
        }
    }

    private void validateWebSearch(Map<String, Object> search) {
        var allowed = Set.of("enable", "search_engine", "search_query", "search_intent",
            "count", "search_domain_filter", "search_recency_filter", "content_size",
            "result_sequence", "search_result", "require_search", "search_prompt");
        rejectUnknown(search, allowed, "web_search");
        var engine = text(search.get("search_engine"));
        if (engine == null || !Set.of("search_std", "search_pro", "search_pro_sogou",
            "search_pro_quark").contains(engine)) {
            throw new IllegalArgumentException("Zhipu web_search.search_engine is required");
        }
        integerRange(search.get("count"), "web_search.count", 1, 50);
        enumValue(search.get("search_recency_filter"), "web_search.search_recency_filter",
            Set.of("oneDay", "oneWeek", "oneMonth", "oneYear", "noLimit"));
        enumValue(search.get("content_size"), "web_search.content_size",
            Set.of("medium", "high"));
        enumValue(search.get("result_sequence"), "web_search.result_sequence",
            Set.of("before", "after"));
        for (var field : List.of("enable", "search_result", "require_search")) {
            booleanValue(search.get(field), "web_search." + field);
        }
    }

    private void validateRetrieval(Map<String, Object> retrieval) {
        rejectUnknown(retrieval, Set.of("knowledge_id", "prompt_template"), "retrieval");
        if (text(retrieval.get("knowledge_id")) == null) {
            throw new IllegalArgumentException("Zhipu retrieval.knowledge_id is required");
        }
    }

    private void validateMcp(Map<String, Object> mcp) {
        rejectUnknown(mcp, Set.of("server_label", "server_url", "transport_type",
            "allowed_tools", "headers"), "mcp");
        if (text(mcp.get("server_label")) == null) {
            throw new IllegalArgumentException("Zhipu mcp.server_label is required");
        }
        enumValue(mcp.get("transport_type"), "mcp.transport_type",
            Set.of("sse", "streamable-http"));
    }

    private void validateIdentifiers(Map<String, Object> body) {
        stringLength(body.get("request_id"), "request_id", 6, 64);
        stringLength(body.get("user_id"), "user_id", 6, 128);
    }

    private void validateStructuredOutput(Map<String, Object> body) {
        var format = map(body.get("response_format"));
        if (format == null) {
            return;
        }
        if (!Set.of("text", "json_object").contains(text(format.get("type")))) {
            throw new IllegalArgumentException(
                "Zhipu response_format.type must be 'text' or 'json_object'");
        }
        if (format.size() != 1) {
            throw new IllegalArgumentException(
                "Zhipu response_format does not document JSON Schema fields");
        }
    }

    private void validateMedia(Map<String, Object> body) {
        var media = mediaStats(body.get("messages"));
        var kinds = media.kinds();
        if (kinds.isEmpty()) {
            return;
        }
        if (kinds.contains("file_url")
            && (kinds.contains("image_url") || kinds.contains("video_url"))) {
            throw new IllegalArgumentException(
                "Zhipu file_url cannot be combined with image_url or video_url");
        }
        if (media.imageCount() > 50) {
            throw new IllegalArgumentException("Zhipu vision requests accept at most 50 images");
        }
        if (media.fileCount() > 50) {
            throw new IllegalArgumentException("Zhipu vision requests accept at most 50 files");
        }
    }

    private MediaStats mediaStats(Object value) {
        if (!(value instanceof List<?> messages)) {
            return MediaStats.empty();
        }
        var media = new MediaStatsAccumulator();
        for (var messageValue : messages) {
            collectMessageMedia(messageValue, media);
        }
        return media.snapshot();
    }

    private void collectMessageMedia(Object value, MediaStatsAccumulator media) {
        var message = map(value);
        if (message == null || !(message.get("content") instanceof List<?> content)) {
            return;
        }
        for (var part : content) {
            media.accept(map(part));
        }
    }

    private void rejectUndocumentedOpenAiFields(Map<String, Object> body) {
        var unsupported = new LinkedHashSet<String>();
        for (var field : Set.of("frequency_penalty", "presence_penalty", "seed", "logprobs",
            "top_logprobs", "parallel_tool_calls", "logit_bias", "n", "modalities",
            "audio", "max_completion_tokens", "verbosity", "store", "metadata",
            "service_tier")) {
            if (body.containsKey(field)) {
                unsupported.add(field);
            }
        }
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("Zhipu Chat does not document field(s): "
                + String.join(", ", unsupported));
        }
    }

    private String mediaReference(Media media, String mime, boolean dataAllowed) {
        var data = media.getData();
        if (data instanceof URI uri) {
            return httpUrl(uri.toString());
        }
        if (data instanceof Resource resource) {
            try {
                var bytes = resource.getContentAsByteArray();
                if (!dataAllowed) {
                    throw new IllegalArgumentException(
                        "Zhipu video and file inputs require an HTTP(S) URL");
                }
                validateImageBytes(bytes);
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Failed to read Zhipu media resource", e);
            }
        }
        if (data instanceof byte[] bytes) {
            if (!dataAllowed) {
                throw new IllegalArgumentException(
                    "Zhipu video and file inputs require an HTTP(S) URL");
            }
            validateImageBytes(bytes);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        }
        var value = data != null ? data.toString() : "";
        if (HTTP_MEDIA_REFERENCES.allows(value)) {
            return httpUrl(value);
        }
        if (dataAllowed) {
            if (DATA_REFERENCES.allows(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException(
            "Zhipu media input requires an HTTP(S) URL"
                + (dataAllowed ? " or data URL" : ""));
    }

    private String rawBase64(Media media) {
        var data = media.getData();
        try {
            if (data instanceof Resource resource) {
                return Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
            }
            if (data instanceof byte[] bytes) {
                return Base64.getEncoder().encodeToString(bytes);
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read Zhipu audio resource", e);
        }
        var value = data != null ? data.toString() : "";
        if (value.isBlank()) {
            throw new IllegalArgumentException("Zhipu audio input requires base64 data");
        }
        if (HTTP_MEDIA_REFERENCES.allows(value)) {
            throw new IllegalArgumentException("Zhipu audio input requires base64 data");
        }
        if (!DATA_REFERENCES.allows(value)) {
            return value;
        }
        var separator = value.indexOf(',');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private void validateImageBytes(byte[] bytes) {
        if (bytes.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Zhipu image inputs must not exceed 5 MiB");
        }
    }

    private String httpUrl(String value) {
        if (!HTTP_MEDIA_REFERENCES.allows(value)) {
            throw new IllegalArgumentException("Zhipu media URL must use HTTP or HTTPS");
        }
        URI.create(value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private void rejectUnknown(Map<String, Object> values, Set<String> allowed, String label) {
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Zhipu " + label + " field(s): "
                + String.join(", ", unknown));
        }
    }

    private void enumValue(Object value, String field, Set<String> allowed) {
        if (value != null && !allowed.contains(text(value))) {
            throw new IllegalArgumentException("Unsupported Zhipu " + field + ": " + value);
        }
    }

    private void booleanValue(Object value, String field) {
        if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("Zhipu " + field + " must be boolean");
        }
    }

    private void decimalRange(Object value, String field, double min, double max) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                "Zhipu " + field + " must be between " + min + " and " + max);
        }
        if (number.doubleValue() < min) {
            throw new IllegalArgumentException(
                "Zhipu " + field + " must be between " + min + " and " + max);
        }
        if (number.doubleValue() > max) {
            throw new IllegalArgumentException(
                "Zhipu " + field + " must be between " + min + " and " + max);
        }
        var scaled = number.doubleValue() * 100d;
        if (Math.abs(scaled - Math.rint(scaled)) > 0.0000001d) {
            throw new IllegalArgumentException("Zhipu " + field + " accepts at most two decimals");
        }
    }

    private void integerRange(Object value, String field, int min, int max) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw invalidInteger(field, min, max);
        }
        if (Math.rint(number.doubleValue()) != number.doubleValue()) {
            throw invalidInteger(field, min, max);
        }
        if (number.longValue() < min) {
            throw invalidInteger(field, min, max);
        }
        if (number.longValue() > max) {
            throw invalidInteger(field, min, max);
        }
    }

    private IllegalArgumentException invalidInteger(String field, int min, int max) {
        return new IllegalArgumentException(
            "Zhipu " + field + " must be an integer between " + min + " and " + max);
    }

    private void stringLength(Object value, String field, int min, int max) {
        if (value == null) {
            return;
        }
        var text = text(value);
        if (text == null) {
            throw new IllegalArgumentException(
                "Zhipu " + field + " length must be between " + min + " and " + max);
        }
        if (text.length() < min) {
            throw new IllegalArgumentException(
                "Zhipu " + field + " length must be between " + min + " and " + max);
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(
                "Zhipu " + field + " length must be between " + min + " and " + max);
        }
    }

    private String join(Set<?> values) {
        return values.stream().map(String::valueOf).sorted()
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private record MediaStats(Set<String> kinds, int imageCount, int fileCount) {
        private static MediaStats empty() {
            return new MediaStats(Set.of(), 0, 0);
        }
    }

    private static final class MediaStatsAccumulator {
        private final Set<String> kinds = new LinkedHashSet<>();
        private int imageCount;
        private int fileCount;

        private void accept(Map<String, Object> part) {
            if (part == null) {
                return;
            }
            var type = part.get("type") instanceof String value ? value : null;
            if (type == null || "text".equals(type)) {
                return;
            }
            kinds.add(type);
            switch (type) {
                case "image_url" -> imageCount++;
                case "file_url" -> fileCount++;
                default -> { }
            }
        }

        private MediaStats snapshot() {
            return new MediaStats(Set.copyOf(kinds), imageCount, fileCount);
        }
    }
}
