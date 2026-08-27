package run.halo.aifoundation.provider.dashscope;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

public final class DashScopeChatModel extends ChatCompletionsModel {

    public DashScopeChatModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DashScopeChatProfile());
    }
}
