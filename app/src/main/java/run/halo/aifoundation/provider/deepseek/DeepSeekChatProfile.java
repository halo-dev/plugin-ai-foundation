package run.halo.aifoundation.provider.deepseek;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;

final class DeepSeekChatProfile implements ChatCompletionsProfile {

    @Override
    public String providerType() {
        return "deepseek";
    }

    @Override
    public String adapterType() {
        return "deepseek-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        validateReasoningEffort(body.get("reasoning_effort"));
        validateStrictTools(body.get("tools"), options);
        var thinking = body.get("thinking") instanceof Map<?, ?> value
            ? value.get("type") : null;
        if ("disabled".equals(thinking)) {
            return;
        }
        body.remove("temperature");
        body.remove("top_p");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        return DeepSeekImageInputs.chatContentPart(media);
    }

    private void validateReasoningEffort(Object value) {
        if (value == null) {
            return;
        }
        if (List.of("low", "medium", "high", "xhigh", "max").contains(value)) {
            return;
        }
        throw new IllegalArgumentException(
            "DeepSeek reasoning_effort must be low, medium, high, xhigh, or max");
    }

    private void validateStrictTools(Object value, ChatCompletionsOptions options) {
        if (!containsStrictTool(value)) {
            return;
        }
        var baseUrl = options.getBaseUrl();
        if (baseUrl != null && baseUrl.matches(".*/beta/?$")) {
            return;
        }
        throw new IllegalArgumentException(
            "DeepSeek strict tools require a provider base URL ending in /beta");
    }

    private boolean containsStrictTool(Object value) {
        if (!(value instanceof Iterable<?> tools)) {
            return false;
        }
        for (var item : tools) {
            if (isStrictTool(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrictTool(Object value) {
        if (!(value instanceof Map<?, ?> tool)) {
            return false;
        }
        if (!(tool.get("function") instanceof Map<?, ?> function)) {
            return false;
        }
        return Boolean.TRUE.equals(function.get("strict"));
    }
}
