package run.halo.aifoundation.provider.mimo;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Xiaomi MiMo Chat Completions model with full-modal and Web Search policy. */
public final class MiMoChatModel extends ChatCompletionsModel {

    public MiMoChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new MiMoChatProfile());
    }
}
