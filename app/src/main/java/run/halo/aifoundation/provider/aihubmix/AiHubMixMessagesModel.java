package run.halo.aifoundation.provider.aihubmix;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** aihubmix adapter for the provider's documented Anthropic Messages endpoint. */
public final class AiHubMixMessagesModel extends AnthropicMessagesModel {

    public AiHubMixMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new StandardAnthropicMessagesProfile(
            "aihubmix", "aihubmix-messages", "/messages",
            StandardAnthropicMessagesProfile.Authentication.BEARER));
    }
}
