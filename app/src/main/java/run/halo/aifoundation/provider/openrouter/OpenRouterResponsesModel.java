package run.halo.aifoundation.provider.openrouter;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** OpenRouter OpenResponses client. */
public final class OpenRouterResponsesModel extends ResponsesModel {

    public OpenRouterResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OpenRouterResponsesProfile());
    }
}
