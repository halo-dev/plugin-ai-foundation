package run.halo.aifoundation.provider.openrouter;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesOutputFormats;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** OpenRouter policy for its Anthropic Messages endpoint. */
final class OpenRouterMessagesProfile implements AnthropicMessagesProfile {

    private static final StandardAnthropicMessagesProfile STANDARD =
        new StandardAnthropicMessagesProfile(
            "openrouter", "openrouter-messages", "/messages",
            StandardAnthropicMessagesProfile.Authentication.BEARER);

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
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        STANDARD.customizeRequest(body, prompt, options, stream);
        AnthropicMessagesOutputFormats.applyJsonSchema(body, options);
    }
}
