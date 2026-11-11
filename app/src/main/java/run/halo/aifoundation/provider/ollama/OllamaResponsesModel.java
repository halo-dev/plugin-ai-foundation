package run.halo.aifoundation.provider.ollama;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** Ollama's documented OpenAI-compatible Responses transport. */
public final class OllamaResponsesModel extends ResponsesModel {

    OllamaResponsesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OllamaResponsesProfile());
    }
}
