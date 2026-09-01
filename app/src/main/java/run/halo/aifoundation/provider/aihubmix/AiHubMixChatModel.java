package run.halo.aifoundation.provider.aihubmix;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** AIHubMix Chat Completions adapter with gateway reasoning replay. */
public final class AiHubMixChatModel extends ChatCompletionsModel {

    public AiHubMixChatModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new AiHubMixChatProfile());
    }
}
