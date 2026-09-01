package run.halo.aifoundation.provider.siliconflow;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** SiliconFlow Chat Completions client with provider-owned request policy. */
public final class SiliconFlowChatModel extends ChatCompletionsModel {

    public SiliconFlowChatModel(ChatCompletionsOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new SiliconFlowChatProfile());
    }
}
