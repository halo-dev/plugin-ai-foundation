package run.halo.aifoundation.provider.dashscope;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;

/** dashscope adapter for the provider's documented Anthropic Messages endpoint. */
public final class DashScopeMessagesModel extends AnthropicMessagesModel {

    public DashScopeMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DashScopeMessagesProfile());
    }
}
