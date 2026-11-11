package run.halo.aifoundation.provider.ollama;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesModel;

/** ollama adapter for the provider's documented Anthropic Messages endpoint. */
public final class OllamaMessagesModel extends AnthropicMessagesModel {

    public OllamaMessagesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new OllamaMessagesProfile());
    }
}
