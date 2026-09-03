package run.halo.aifoundation.provider.openai;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** OpenAI-owned Chat Completions adapter, intentionally separate from Responses. */
public final class OpenAiChatModel extends ChatCompletionsModel {

    public OpenAiChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OpenAiChatProfile());
    }
}
