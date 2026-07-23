package run.halo.aifoundation.service.language.stream;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Selects the richest available provider stream contract for one chat model.
 */
public final class ProviderStreamingChatModels {

    private ProviderStreamingChatModels() {
    }

    public static ProviderStreamingChatModel adapt(ChatModel chatModel) {
        return chatModel instanceof ProviderStreamingChatModel streaming
            ? streaming
            : new FinalOnlyProviderStreamingChatModel(chatModel);
    }
}
