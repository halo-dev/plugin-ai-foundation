package run.halo.aifoundation.provider.mimo;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;

/** MiMo Messages sampling and tool-choice rules. */
final class MiMoMessagesProfile implements AnthropicMessagesProfile {

    @Override
    public String providerType() {
        return "mimo";
    }

    @Override
    public String adapterType() {
        return "mimo-messages";
    }

    @Override
    public String endpointPath() {
        return "/anthropic/v1/messages";
    }

    @Override
    public void applyHeaders(HttpHeaders headers, ChatCompletionsOptions options) {
        headers.set("anthropic-version", "2023-06-01");
        var apiKey = options.getApiKey();
        if (apiKey == null) {
            return;
        }
        if (apiKey.isBlank()) {
            return;
        }
        headers.set("api-key", apiKey);
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        body.putIfAbsent("max_tokens", 4096);
        range(body.get("temperature"), "temperature", 0d, 1.5d);
        range(body.get("top_p"), "top_p", 0.01d, 1d);
        normalizeToolChoice(body);
        normalizeThinkingSampling(body);
    }

    private void normalizeToolChoice(Map<String, Object> body) {
        if (!(body.get("tool_choice") instanceof Map<?, ?> choice)) {
            return;
        }
        if ("auto".equals(choice.get("type"))) {
            return;
        }
        body.remove("tool_choice");
    }

    private void normalizeThinkingSampling(Map<String, Object> body) {
        if (body.get("thinking") instanceof Map<?, ?> thinking) {
            if ("disabled".equals(thinking.get("type"))) {
                return;
            }
        }
        body.remove("temperature");
        body.remove("top_p");
    }

    private void range(Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            var numeric = number.doubleValue();
            if (numeric < minimum) {
                throw rangeError(field, minimum, maximum);
            }
            if (numeric > maximum) {
                throw rangeError(field, minimum, maximum);
            }
            return;
        }
        throw rangeError(field, minimum, maximum);
    }

    private IllegalArgumentException rangeError(String field, double minimum, double maximum) {
        return new IllegalArgumentException(
            "MiMo Messages " + field + " must be between " + minimum + " and " + maximum);
    }
}
