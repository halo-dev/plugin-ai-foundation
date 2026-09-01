package run.halo.aifoundation.provider.gitee;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

public final class GiteeChatModel extends ChatCompletionsModel {

    public GiteeChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new GiteeChatProfile());
    }
}
