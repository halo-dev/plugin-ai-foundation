package run.halo.aifoundation.provider.deepseek;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

public final class DeepSeekChatModel extends ChatCompletionsModel {

    public DeepSeekChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new DeepSeekChatProfile());
    }
}
