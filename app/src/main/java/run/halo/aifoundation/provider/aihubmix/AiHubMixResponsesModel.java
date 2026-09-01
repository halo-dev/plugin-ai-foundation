package run.halo.aifoundation.provider.aihubmix;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** AIHubMix Responses adapter used for Responses-only and cross-provider models. */
public final class AiHubMixResponsesModel extends ResponsesModel {

    public AiHubMixResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new AiHubMixResponsesProfile());
    }
}
