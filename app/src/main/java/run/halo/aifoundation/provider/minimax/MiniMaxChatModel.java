package run.halo.aifoundation.provider.minimax;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Explicit MiniMax OpenAI-compatible Chat Completions client. */
public final class MiniMaxChatModel extends ChatCompletionsModel {

    public MiniMaxChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new MiniMaxChatProfile());
    }
}
