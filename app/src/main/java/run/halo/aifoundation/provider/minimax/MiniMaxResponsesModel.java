package run.halo.aifoundation.provider.minimax;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** MiniMax's documented OpenAI Responses-compatible adapter. */
public final class MiniMaxResponsesModel extends ResponsesModel {

    public MiniMaxResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new MiniMaxResponsesProfile());
    }
}
