package run.halo.aifoundation.provider.zhipu;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** zhipu adapter for the provider's documented Anthropic Messages endpoint. */
public final class ZhiPuMessagesModel extends AnthropicMessagesModel {

    public ZhiPuMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new StandardAnthropicMessagesProfile(
            "zhipu", "zhipu-messages", "/api/anthropic/v1/messages",
            StandardAnthropicMessagesProfile.Authentication.X_API_KEY));
    }
}
