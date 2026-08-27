package run.halo.aifoundation.provider.openrouter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

/** OpenRouter policy for its beta OpenResponses endpoint and router-specific options. */
final class OpenRouterResponsesProfile implements ResponsesProfile {

    @Override
    public String providerType() {
        return "openrouter";
    }

    @Override
    public String adapterType() {
        return "openrouter-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        body.put("store", false);
        OpenRouterRoutingOptions.validate(body.get("provider"), "responses");
        appendServerTools(body);
    }

    @Override
    public Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
        return metadata == null || metadata.isEmpty()
            ? Map.of() : Map.of("openrouter", Map.copyOf(metadata));
    }

    private void appendServerTools(Map<String, Object> body) {
        var value = body.remove("serverTools");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> builtins)) {
            throw new IllegalArgumentException("OpenRouter serverTools must be an array");
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> existing) {
            tools.addAll(existing);
        }
        for (var tool : builtins) {
            validateServerTool(tool);
            tools.add(tool);
        }
        body.put("tools", List.copyOf(tools));
    }

    private void validateServerTool(Object value) {
        if (!(value instanceof Map<?, ?> tool)) {
            throw new IllegalArgumentException("OpenRouter serverTools entries must be objects");
        }
        var type = tool.get("type");
        if (isServerToolType(type)) {
            return;
        }
        throw new IllegalArgumentException(
            "OpenRouter serverTools entries require a non-function type");
    }

    private boolean isServerToolType(Object type) {
        if (!(type instanceof String text)) {
            return false;
        }
        if (text.isBlank()) {
            return false;
        }
        return !"function".equals(text);
    }
}
