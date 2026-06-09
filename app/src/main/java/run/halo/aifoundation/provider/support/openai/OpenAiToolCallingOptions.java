package run.halo.aifoundation.provider.support.openai;

import java.util.Map;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.tool.ToolChoice;

/**
 * Builds OpenAI-compatible tool calling options from the public SDK request shape.
 */
public final class OpenAiToolCallingOptions {

    private OpenAiToolCallingOptions() {
    }

    public static OpenAiCompatibleChatOptions build(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames) {
        return build(request, toolCallbacks, toolNames, ReasoningControlOptions.unsupported());
    }

    public static OpenAiCompatibleChatOptions build(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames,
        ReasoningControlOptions reasoningControlOptions) {
        var builder = OpenAiCompatibleChatOptions.builder()
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
        reasoningControlOptions.apply(builder, request);
        applyToolChoice(builder, request.getToolChoice(), toolNames);
        OpenAiStructuredOutputOptions.apply(builder, request);
        return builder.build();
    }

    public static void applyNativeTools(OpenAiCompatibleChatOptions.Builder builder,
        GenerateTextRequest request) {
        // RC1 derives provider tool declarations from ToolCallback definitions.
    }

    public static void applyToolChoice(OpenAiCompatibleChatOptions.Builder builder, ToolChoice toolChoice,
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
