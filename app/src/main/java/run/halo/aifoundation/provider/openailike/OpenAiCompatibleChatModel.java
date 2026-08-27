package run.halo.aifoundation.provider.openailike;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.StandardChatCompletionsProfile;

/**
 * Configurable OpenAI-compatible fallback over the reusable Chat Completions protocol.
 */
public final class OpenAiCompatibleChatModel extends ChatCompletionsModel {

    public OpenAiCompatibleChatModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder,
            new StandardChatCompletionsProfile("openai-compatible", "openai-compatible"));
    }
}
