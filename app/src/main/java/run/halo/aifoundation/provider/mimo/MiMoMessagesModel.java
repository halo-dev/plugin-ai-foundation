package run.halo.aifoundation.provider.mimo;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;

/** mimo adapter for the provider's documented Anthropic Messages endpoint. */
public final class MiMoMessagesModel extends AnthropicMessagesModel {

    public MiMoMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new MiMoMessagesProfile());
    }
}
