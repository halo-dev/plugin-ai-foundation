package run.halo.aifoundation.provider.gitee;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** gitee adapter for the provider's documented Anthropic Messages endpoint. */
public final class GiteeMessagesModel extends AnthropicMessagesModel {

    public GiteeMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new StandardAnthropicMessagesProfile(
            "gitee", "gitee-messages", "/messages",
            StandardAnthropicMessagesProfile.Authentication.BEARER));
    }
}
