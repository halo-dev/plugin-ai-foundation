package run.halo.aifoundation.provider.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

/** OpenAI Responses policy for stateless reasoning and hosted tools. */
final class OpenAiResponsesProfile implements ResponsesProfile {

    private static final String ENCRYPTED_REASONING = "reasoning.encrypted_content";

    @Override
    public String providerType() {
        return "openai";
    }

    @Override
    public String adapterType() {
        return "openai-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        body.putIfAbsent("store", false);
        appendHostedTools(body);
        requestEncryptedReasoningForStatelessCalls(body);
    }

    private void appendHostedTools(Map<String, Object> body) {
        var value = body.remove("builtinTools");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> hostedTools)) {
            throw new IllegalArgumentException("OpenAI builtinTools must be an array");
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> functions) {
            tools.addAll(functions);
        }
        for (var tool : hostedTools) {
            validateHostedTool(tool);
            tools.add(tool);
        }
        body.put("tools", List.copyOf(tools));
    }

    private void requestEncryptedReasoningForStatelessCalls(Map<String, Object> body) {
        if (!Boolean.FALSE.equals(body.get("store"))) {
            return;
        }
        var include = new ArrayList<String>();
        if (body.get("include") instanceof List<?> values) {
            values.stream().map(Object::toString).forEach(include::add);
        }
        if (!include.contains(ENCRYPTED_REASONING)) {
            include.add(ENCRYPTED_REASONING);
        }
        body.put("include", List.copyOf(include));
    }

    private void validateHostedTool(Object value) {
        if (!(value instanceof Map<?, ?> tool)) {
            throw new IllegalArgumentException("OpenAI builtinTools entries must be objects");
        }
        var type = tool.get("type");
        if (isHostedToolType(type)) {
            return;
        }
        throw new IllegalArgumentException(
            "OpenAI builtinTools entries require a hosted-tool type");
    }

    private boolean isHostedToolType(Object type) {
        if (!(type instanceof String text)) {
            return false;
        }
        if (text.isBlank()) {
            return false;
        }
        return !"function".equals(text);
    }
}
