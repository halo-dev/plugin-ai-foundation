package run.halo.aifoundation.provider.openai;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;

/** OpenAI Chat Completions policy for adapter-specific option validation. */
final class OpenAiChatProfile implements ChatCompletionsProfile {

    @Override
    public String providerType() {
        return "openai";
    }

    @Override
    public String adapterType() {
        return "openai-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        if (!body.containsKey("builtinTools")) {
            return;
        }
        throw new IllegalArgumentException(
            "OpenAI builtinTools require the openai-responses adapter");
    }
}
