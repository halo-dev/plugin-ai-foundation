package run.halo.aifoundation.provider.kimi;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Kimi Chat Completions model with Moonshot-native request policy. */
public final class KimiChatModel extends ChatCompletionsModel {

    public KimiChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new KimiChatProfile());
    }
}
