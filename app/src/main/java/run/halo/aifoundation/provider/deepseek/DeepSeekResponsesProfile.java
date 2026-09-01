package run.halo.aifoundation.provider.deepseek;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;
import run.halo.aifoundation.provider.protocol.responses.ResponsesOutputReplay;

/** DeepSeek policy for its stateless Responses compatibility surface. */
final class DeepSeekResponsesProfile implements ResponsesProfile {

    private static final Set<String> REPLAYED_OUTPUT_TYPES =
        Set.of("reasoning", "web_search_call");
    private static final List<String> IGNORED_FIELDS = List.of(
        "store", "previous_response_id", "conversation", "background", "metadata", "include",
        "prompt", "truncation", "service_tier", "safety_identifier", "prompt_cache_key",
        "prompt_cache_retention", "context_management", "parallel_tool_calls", "max_tool_calls",
        "stream_options");

    @Override
    public String providerType() {
        return "deepseek";
    }

    @Override
    public String adapterType() {
        return "deepseek-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        removeIgnoredFields(body);
        removeUnsupportedToolFields(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        return DeepSeekImageInputs.responsesContentPart(media);
    }

    @Override
    public List<Map<String, Object>> assistantInputItems(AssistantMessage message) {
        return ResponsesOutputReplay.assistantInputItems(
            message, providerType(), REPLAYED_OUTPUT_TYPES);
    }

    private void removeIgnoredFields(Map<String, Object> body) {
        IGNORED_FIELDS.forEach(body::remove);
    }

    private void removeUnsupportedToolFields(Map<String, Object> body) {
        if (!(body.get("tools") instanceof List<?> tools)) {
            return;
        }
        var normalized = new ArrayList<Object>(tools.size());
        tools.forEach(tool -> normalized.add(withoutStrict(tool)));
        body.put("tools", normalized);
    }

    private Object withoutStrict(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return value;
        }
        var tool = new LinkedHashMap<String, Object>();
        source.forEach((key, fieldValue) -> tool.put(String.valueOf(key), fieldValue));
        tool.remove("strict");
        return tool;
    }

}
