package run.halo.aifoundation.provider.deepseek;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;

/** deepseek adapter for the provider's documented Anthropic Messages endpoint. */
public final class DeepSeekMessagesModel extends AnthropicMessagesModel {

    public DeepSeekMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DeepSeekMessagesProfile());
    }
}
