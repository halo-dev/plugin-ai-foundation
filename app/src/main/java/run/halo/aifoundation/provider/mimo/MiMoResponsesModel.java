package run.halo.aifoundation.provider.mimo;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** Default Xiaomi MiMo Responses model with native reasoning replay. */
public final class MiMoResponsesModel extends ResponsesModel {

    public MiMoResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new MiMoResponsesProfile());
    }
}
