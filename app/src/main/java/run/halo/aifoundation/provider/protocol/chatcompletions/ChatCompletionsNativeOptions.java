package run.halo.aifoundation.provider.protocol.chatcompletions;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;

/** Applies administrator-owned model options to the shared Chat Completions option type. */
public final class ChatCompletionsNativeOptions {

    private ChatCompletionsNativeOptions() {
    }

    public static ChatOptions apply(ChatOptions options, Map<String, Object> nativeOptions) {
        if (!(options instanceof ChatCompletionsOptions chatOptions)) {
            return options;
        }
        if (nativeOptions == null) {
            return options;
        }
        if (nativeOptions.isEmpty()) {
            return options;
        }
        var extraBody = new LinkedHashMap<String, Object>(nativeOptions);
        if (chatOptions.getExtraBody() != null) {
            extraBody.putAll(chatOptions.getExtraBody());
        }
        return chatOptions.mutate().extraBody(Map.copyOf(extraBody)).build();
    }
}
