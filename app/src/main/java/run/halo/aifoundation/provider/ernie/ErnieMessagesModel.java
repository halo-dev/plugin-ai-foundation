package run.halo.aifoundation.provider.ernie;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** ernie adapter for the provider's documented Anthropic Messages endpoint. */
public final class ErnieMessagesModel extends AnthropicMessagesModel {

    public ErnieMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new StandardAnthropicMessagesProfile(
            "ernie", "ernie-messages", "/anthropic/v1/messages",
            StandardAnthropicMessagesProfile.Authentication.X_API_KEY));
    }
}
