package run.halo.aifoundation.provider.support;

import java.util.Map;
import java.util.Set;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.ToolChoice;

/**
 * Builds OpenAI-compatible tool calling options from the public SDK request shape.
 */
public final class OpenAiToolCallingOptions {

    private OpenAiToolCallingOptions() {
    }

    public static OpenAiChatOptions build(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames) {
        var builder = OpenAiChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stop(request.getStopSequences())
            .internalToolExecutionEnabled(false)
            .toolCallbacks(toolCallbacks)
            .httpHeaders(headers(request));
        applyToolChoice(builder, request.getToolChoice(), toolNames);
        OpenAiStructuredOutputOptions.apply(builder, request);
        return builder.build();
    }

    public static void applyToolChoice(OpenAiChatOptions.Builder builder, ToolChoice toolChoice,
        Set<String> toolNames) {
        if (toolChoice == null || toolChoice.getType() == null
            || toolChoice.getType() == ToolChoice.Type.AUTO) {
            builder.toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.AUTO);
            return;
        }
        switch (toolChoice.getType()) {
            case NONE -> builder.toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.NONE);
            case REQUIRED -> builder.toolChoice("required");
            case TOOL -> {
                builder.toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.function(
                    toolChoice.getToolName()));
                if (toolNames != null && !toolNames.isEmpty()) {
                    builder.toolNames(toolNames);
                }
            }
            default -> {
            }
        }
    }

    private static Map<String, String> headers(GenerateTextRequest request) {
        return request.getHeaders() != null ? request.getHeaders() : Map.of();
    }
}
