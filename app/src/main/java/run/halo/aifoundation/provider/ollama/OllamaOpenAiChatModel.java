package run.halo.aifoundation.provider.ollama;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.StandardChatCompletionsProfile;

/** Ollama's documented OpenAI-compatible Chat Completions adapter. */
public final class OllamaOpenAiChatModel extends ChatCompletionsModel {

    public OllamaOpenAiChatModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder,
            new StandardChatCompletionsProfile("ollama", "ollama-openai-chat"));
    }
}
