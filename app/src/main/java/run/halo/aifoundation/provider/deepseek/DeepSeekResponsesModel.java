package run.halo.aifoundation.provider.deepseek;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** DeepSeek's stateless Responses API adapter. */
public final class DeepSeekResponsesModel extends ResponsesModel {

    public DeepSeekResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DeepSeekResponsesProfile());
    }
}
