package run.halo.aifoundation.provider.siliconflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

/** SiliconFlow-specific Chat, reasoning, FIM, media, and tool constraints. */
final class SiliconFlowChatProfile implements ChatCompletionsProfile {

    private static final int MAX_TOOLS = 128;
    private static final Set<String> EXTRA_FIELDS = Set.of(
        "enable_thinking", "thinking_budget", "min_p", "top_k", "prefix", "suffix");

    @Override
    public String providerType() {
        return "siliconflow";
    }

    @Override
    public String adapterType() {
        return "siliconflow-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        validateExtraFields(options.getExtraBody());
        validateThinking(body);
        validateFim(body);
        validateTools(body);
        rejectUndocumentedPortableFields(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("image/")) {
            return urlContentPart("image_url", media);
        }
        if (mime.startsWith("video/")) {
            return urlContentPart("video_url", media);
        }
        if (mime.startsWith("audio/")) {
            return urlContentPart("audio_url", media);
        }
        throw new IllegalArgumentException(
            "SiliconFlow Chat supports image, video, and audio media, received: " + mime);
    }

    @Override
    public String reasoningContent(JsonNode message) {
        var value = message.path("reasoning_content");
        return value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
    }

    @Override
    public void customizeAssistantMessage(Map<String, Object> body,
        AssistantMessage message) {
        // SiliconFlow requires exact reasoning_content replay for Interleaved Thinking models.
        // The common codec already copied the untouched metadata value into this field.
    }

    private void validateExtraFields(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(EXTRA_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported SiliconFlow chat option(s): "
                + String.join(", ", unknown));
        }
    }

    private void validateThinking(Map<String, Object> body) {
        var enabled = body.get("enable_thinking");
        if (enabled != null && !(enabled instanceof Boolean)) {
            throw new IllegalArgumentException("SiliconFlow enable_thinking must be boolean");
        }
        var budget = integer(body.get("thinking_budget"), "thinking_budget");
        if (budget != null && (budget < 128 || budget > 32768)) {
            throw new IllegalArgumentException(
                "SiliconFlow thinking_budget must be between 128 and 32768");
        }
        var minP = decimal(body.get("min_p"), "min_p");
        if (minP != null && (minP < 0d || minP > 1d)) {
            throw new IllegalArgumentException("SiliconFlow min_p must be between 0 and 1");
        }
        decimal(body.get("top_k"), "top_k");
    }

    private void validateFim(Map<String, Object> body) {
        var prefix = text(body.get("prefix"));
        var suffix = text(body.get("suffix"));
        if (prefix == null && suffix == null) {
            return;
        }
        if (prefix == null || suffix == null) {
            throw new IllegalArgumentException(
                "SiliconFlow Chat FIM requires both prefix and suffix");
        }
        if (body.get("tools") instanceof List<?> tools && !tools.isEmpty()) {
            throw new IllegalArgumentException(
                "SiliconFlow Chat FIM cannot be combined with tool calling");
        }
        if (body.containsKey("response_format")) {
            throw new IllegalArgumentException(
                "SiliconFlow Chat FIM cannot be combined with structured output");
        }
    }

    private void validateTools(Map<String, Object> body) {
        if (!(body.get("tools") instanceof List<?> tools)) {
            return;
        }
        if (tools.size() > MAX_TOOLS) {
            throw new IllegalArgumentException("SiliconFlow accepts at most 128 tools");
        }
        var names = new HashSet<String>();
        for (var item : tools) {
            if (item instanceof Map<?, ?> tool
                && tool.get("function") instanceof Map<?, ?> function) {
                var name = text(function.get("name"));
                if (name != null && !names.add(name)) {
                    throw new IllegalArgumentException(
                        "SiliconFlow tool function names must be unique: " + name);
                }
            }
        }
    }

    private void rejectUndocumentedPortableFields(Map<String, Object> body) {
        for (var field : List.of("seed", "presence_penalty", "parallel_tool_calls",
            "logprobs", "top_logprobs")) {
            if (body.containsKey(field)) {
                throw new IllegalArgumentException(
                    "SiliconFlow Chat does not document request field: " + field);
            }
        }
    }

    private Map<String, Object> urlContentPart(String type, Media media) {
        var reference = MediaContentSources.urlOrDataUrl(media, "SiliconFlow media");
        return Map.of("type", type, type, Map.of("url", reference));
    }

    private Integer integer(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number && Math.rint(number.doubleValue()) == number.doubleValue()) {
            return number.intValue();
        }
        throw new IllegalArgumentException("SiliconFlow " + field + " must be an integer");
    }

    private Double decimal(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("SiliconFlow " + field + " must be numeric");
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

}
