package run.halo.aifoundation.provider.gitee;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;

/** Gitee AI Chat Completions policy, including its guided JSON wire format. */
final class GiteeChatProfile implements ChatCompletionsProfile {

    @Override
    public String providerType() {
        return "gitee-moark";
    }

    @Override
    public String adapterType() {
        return "gitee-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        var responseFormat = body.remove("response_format");
        var schema = jsonSchema(responseFormat);
        if (schema == null) {
            return;
        }
        body.put("guided_json", schema);
    }

    private Object jsonSchema(Object responseFormat) {
        if (!(responseFormat instanceof Map<?, ?> format)) {
            return null;
        }
        if (!"json_schema".equals(format.get("type"))) {
            return null;
        }
        if (!(format.get("json_schema") instanceof Map<?, ?> jsonSchema)) {
            return null;
        }
        return jsonSchema.get("schema");
    }
}
