package run.halo.aifoundation.provider.ernie;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

public final class ErnieResponsesModel extends ResponsesModel {

    public ErnieResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new ErnieResponsesProfile());
    }
}
