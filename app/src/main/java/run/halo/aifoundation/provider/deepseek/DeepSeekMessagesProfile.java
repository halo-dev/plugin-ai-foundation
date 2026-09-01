package run.halo.aifoundation.provider.deepseek;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** DeepSeek policy for its Anthropic Messages compatibility contract. */
final class DeepSeekMessagesProfile implements AnthropicMessagesProfile {

    private static final StandardAnthropicMessagesProfile STANDARD =
        new StandardAnthropicMessagesProfile(
            "deepseek", "deepseek-messages", "/anthropic/v1/messages",
            StandardAnthropicMessagesProfile.Authentication.X_API_KEY);

    @Override
    public String providerType() {
        return STANDARD.providerType();
    }

    @Override
    public String adapterType() {
        return STANDARD.adapterType();
    }

    @Override
    public String endpointPath() {
        return STANDARD.endpointPath();
    }

    @Override
    public void applyHeaders(HttpHeaders headers, ChatCompletionsOptions options) {
        STANDARD.applyHeaders(headers, options);
        headers.set("anthropic-beta", "files-api-2025-04-14");
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        STANDARD.customizeRequest(body, prompt, options, stream);
        validateTemperature(body.get("temperature"));
        validateMetadata(body.get("metadata"));
        removeIgnoredFields(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media, ChatCompletionsOptions options) {
        return DeepSeekImageInputs.messagesContentPart(media);
    }

    private void validateTemperature(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw temperatureError();
        }
        var temperature = number.doubleValue();
        if (temperature < 0) {
            throw temperatureError();
        }
        if (temperature > 2) {
            throw temperatureError();
        }
    }

    private IllegalArgumentException temperatureError() {
        return new IllegalArgumentException(
            "DeepSeek Messages temperature must be between 0 and 2");
    }

    private void validateMetadata(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> metadata)) {
            throw new IllegalArgumentException("DeepSeek Messages metadata must be an object");
        }
        var unsupported = new LinkedHashSet<>(metadata.keySet());
        unsupported.remove("user_id");
        if (unsupported.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException(
            "DeepSeek Messages metadata supports only user_id");
    }

    private void removeIgnoredFields(Map<String, Object> body) {
        for (var field : List.of("service_tier", "top_k", "container", "mcp_servers")) {
            body.remove(field);
        }
    }
}
