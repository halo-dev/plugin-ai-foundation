package run.halo.aifoundation.provider.protocol.chatcompletions;

import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.tool.ToolChoice;

/** Builds Chat Completions tool options from the provider-neutral SDK request. */
public final class ChatCompletionsToolCallingOptions {

    private ChatCompletionsToolCallingOptions() {
    }

    public static ChatCompletionsOptions build(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames) {
        var builder = ChatCompletionsOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .toolCallbacks(toolCallbacks)
            .customHeaders(headers(request));
        applyNativeTools(builder, request);
        applyToolChoice(builder, request.getToolChoice(), toolNames);
        ChatCompletionsStructuredOutputOptions.apply(builder, request);
        return builder.build();
    }

    public static void applyNativeTools(ChatCompletionsOptions.Builder builder,
        GenerateTextRequest request) {
        var strictByToolName = new LinkedHashMap<String, Boolean>();
        if (request.getTools() != null) {
            request.getTools().stream()
                .filter(tool -> tool != null && tool.getName() != null
                    && tool.getStrict() != null)
                .forEach(tool -> strictByToolName.put(tool.getName(), tool.getStrict()));
        }
        builder.toolStrict(strictByToolName);
    }

    public static void applyToolChoice(ChatCompletionsOptions.Builder builder, ToolChoice toolChoice,
        Set<String> toolNames) {
        if (toolChoice == null || toolChoice.getType() == null
            || toolChoice.getType() == ToolChoice.Type.AUTO) {
            builder.toolChoice("auto");
            return;
        }
        switch (toolChoice.getType()) {
            case NONE -> builder.toolChoice("none");
            case REQUIRED -> builder.toolChoice("required");
            case TOOL -> {
                var function = new java.util.LinkedHashMap<String, Object>();
                function.put("name", toolChoice.getToolName());
                var choice = new java.util.LinkedHashMap<String, Object>();
                choice.put("type", "function");
                choice.put("function", function);
                builder.toolChoice(choice);
            }
            default -> {
            }
        }
    }

    private static Map<String, String> headers(GenerateTextRequest request) {
        return request.getHeaders() != null ? request.getHeaders() : Map.of();
    }
}
