package run.halo.aifoundation.provider.dashscope;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesModel;

/** DashScope OpenAI-compatible Responses client. */
public final class DashScopeResponsesModel extends ResponsesModel {

    public DashScopeResponsesModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DashScopeResponsesProfile());
    }
}
