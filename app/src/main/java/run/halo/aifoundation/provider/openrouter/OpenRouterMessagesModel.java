package run.halo.aifoundation.provider.openrouter;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;

/** openrouter adapter for the provider's documented Anthropic Messages endpoint. */
public final class OpenRouterMessagesModel extends AnthropicMessagesModel {

    public OpenRouterMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OpenRouterMessagesProfile());
    }
}
