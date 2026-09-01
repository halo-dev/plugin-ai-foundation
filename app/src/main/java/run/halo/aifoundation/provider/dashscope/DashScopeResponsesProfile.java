package run.halo.aifoundation.provider.dashscope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

/** DashScope Responses policy for built-in tools and stateless execution. */
final class DashScopeResponsesProfile implements ResponsesProfile {

    private static final Set<String> BUILTIN_TOOL_TYPES = Set.of(
        "web_search", "web_extractor", "code_interpreter", "web_search_image",
        "image_search", "file_search", "mcp");

    @Override
    public String providerType() {
        return "dashscope";
    }

    @Override
    public String adapterType() {
        return "dashscope-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        // The runtime replays canonical messages and does not rely on provider-side response state.
        body.putIfAbsent("store", false);
        appendBuiltinTools(body);
    }

    private void appendBuiltinTools(Map<String, Object> body) {
        var value = body.remove("builtinTools");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> builtins)) {
            throw new IllegalArgumentException("DashScope builtinTools must be an array");
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> existing) {
            tools.addAll(existing);
        }
        for (var item : builtins) {
            validateBuiltinTool(item);
            tools.add(item);
        }
        if (!tools.isEmpty()) {
            body.put("tools", List.copyOf(tools));
        }
    }

    private void validateBuiltinTool(Object value) {
        if (!(value instanceof Map<?, ?> tool)) {
            throw new IllegalArgumentException("DashScope builtinTools entries must be objects");
        }
        var type = tool.get("type");
        if (type instanceof String text && BUILTIN_TOOL_TYPES.contains(text)) {
            return;
        }
        throw new IllegalArgumentException(
            "Unsupported DashScope Responses built-in tool type: " + type);
    }
}
