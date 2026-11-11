package run.halo.aifoundation.provider.ernie;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

public final class ErnieChatModel extends ChatCompletionsModel {

    public ErnieChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new ErnieChatProfile());
    }
}
