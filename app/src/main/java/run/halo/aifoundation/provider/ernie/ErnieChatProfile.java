package run.halo.aifoundation.provider.ernie;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;

final class ErnieChatProfile implements ChatCompletionsProfile {

    @Override
    public String providerType() {
        return "ernie";
    }

    @Override
    public String adapterType() {
        return "ernie-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        body.remove("builtinTools");
        body.remove("store");
        var thinking = body.remove("thinking");
        if (thinking instanceof Map<?, ?> value) {
            var type = value.get("type");
            if ("enabled".equals(type) || "disabled".equals(type)) {
                body.put("enable_thinking", "enabled".equals(type));
            }
        }
    }
}
