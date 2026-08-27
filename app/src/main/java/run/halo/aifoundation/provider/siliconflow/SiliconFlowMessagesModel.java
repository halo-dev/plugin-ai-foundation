package run.halo.aifoundation.provider.siliconflow;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;

/** siliconflow adapter for the provider's documented Anthropic Messages endpoint. */
public final class SiliconFlowMessagesModel extends AnthropicMessagesModel {

    public SiliconFlowMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new StandardAnthropicMessagesProfile(
            "siliconflow", "siliconflow-messages", "/messages",
            StandardAnthropicMessagesProfile.Authentication.BEARER));
    }
}
