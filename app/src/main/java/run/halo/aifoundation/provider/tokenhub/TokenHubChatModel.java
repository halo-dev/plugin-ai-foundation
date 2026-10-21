package run.halo.aifoundation.provider.tokenhub;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** TokenHub Chat Completions model with provider-owned reasoning and media policy. */
public final class TokenHubChatModel extends ChatCompletionsModel {

    public TokenHubChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new TokenHubChatProfile());
    }
}
