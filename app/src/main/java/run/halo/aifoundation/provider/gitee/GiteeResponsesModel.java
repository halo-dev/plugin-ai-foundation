package run.halo.aifoundation.provider.gitee;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

public final class GiteeResponsesModel extends ResponsesModel {

    public GiteeResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new GiteeResponsesProfile());
    }
}
