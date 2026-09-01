package run.halo.aifoundation.provider.minimax;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;

/** MiniMax native Messages client using the provider's recommended Anthropic contract. */
public final class MiniMaxMessagesModel extends AnthropicMessagesModel {

    public MiniMaxMessagesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new MiniMaxMessagesProfile());
    }
}
