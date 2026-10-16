package run.halo.aifoundation.provider.tokenhub;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.StandardChatCompletionsProfile;

/** Provider-owned adapter using the shared standard Chat Completions protocol. */
public final class TokenHubChatModel extends ChatCompletionsModel {

    public TokenHubChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder,
            new StandardChatCompletionsProfile("tokenhub", "tokenhub-chat"));
    }
}
