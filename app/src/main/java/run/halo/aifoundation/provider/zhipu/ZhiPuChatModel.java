package run.halo.aifoundation.provider.zhipu;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Zhipu Chat Completions model with GLM-native policy and multimodal conversion. */
public final class ZhiPuChatModel extends ChatCompletionsModel {

    public ZhiPuChatModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder, new ZhiPuChatProfile());
    }
}
