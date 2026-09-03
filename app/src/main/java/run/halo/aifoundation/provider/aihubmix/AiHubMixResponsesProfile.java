package run.halo.aifoundation.provider.aihubmix;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

/** Gateway-specific Responses policy for native tools and reasoning constraints. */
final class AiHubMixResponsesProfile implements ResponsesProfile {

    private static final Set<String> BUILTIN_TOOL_TYPES = Set.of(
        "web_search_preview", "image_generation", "code_interpreter", "mcp",
        "computer_use_preview");

    @Override
    public String providerType() {
        return "aihubmix";
    }

    @Override
    public String adapterType() {
        return "aihubmix-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        appendBuiltinTools(body);
        validateReasoning(body);
        validateToolChoice(body.get("tool_choice"));
    }

    private void appendBuiltinTools(Map<String, Object> body) {
        var value = body.remove("builtinTools");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> builtins)) {
            throw new IllegalArgumentException("AIHubMix builtinTools must be an array");
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> existing) {
            tools.addAll(existing);
        }
        for (var item : builtins) {
            if (!(item instanceof Map<?, ?> tool)
                || !(tool.get("type") instanceof String type)
                || !BUILTIN_TOOL_TYPES.contains(type)) {
                throw new IllegalArgumentException(
                    "AIHubMix Responses builtin tool type must be one of: "
                        + String.join(", ", BUILTIN_TOOL_TYPES));
            }
            tools.add(item);
        }
        if (!tools.isEmpty()) {
            body.put("tools", List.copyOf(tools));
        }
    }

    private void validateReasoning(Map<String, Object> body) {
        if (!(body.get("reasoning") instanceof Map<?, ?> reasoning)
            || reasoning.get("effort") == null) {
            return;
        }
        var effort = reasoning.get("effort").toString().toLowerCase(Locale.ROOT);
        var allowed = Set.of("none", "minimal", "low", "medium", "high");
        if (!allowed.contains(effort)) {
            throw new IllegalArgumentException("Unsupported AIHubMix reasoning effort: " + effort);
        }
    }

    private void validateToolChoice(Object value) {
        if (value == null || value instanceof Map<?, ?>) {
            return;
        }
        if (!(value instanceof String choice)
            || !new LinkedHashSet<>(List.of("auto", "none", "required")).contains(choice)) {
            throw new IllegalArgumentException("Unsupported AIHubMix Responses tool_choice");
        }
    }
}
