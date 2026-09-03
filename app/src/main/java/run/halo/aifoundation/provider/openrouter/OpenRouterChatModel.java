package run.halo.aifoundation.provider.openrouter;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** OpenRouter-owned Chat Completions adapter. */
public final class OpenRouterChatModel extends ChatCompletionsModel {

    public OpenRouterChatModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OpenRouterChatProfile());
    }
}
