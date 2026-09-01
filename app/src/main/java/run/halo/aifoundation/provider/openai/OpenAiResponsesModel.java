package run.halo.aifoundation.provider.openai;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

public final class OpenAiResponsesModel extends ResponsesModel {

    public OpenAiResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OpenAiResponsesProfile());
    }
}
