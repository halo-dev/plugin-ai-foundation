package run.halo.aifoundation.provider.doubao;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

public final class DouBaoResponsesModel extends ResponsesModel {

    public DouBaoResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DouBaoResponsesProfile());
    }
}
