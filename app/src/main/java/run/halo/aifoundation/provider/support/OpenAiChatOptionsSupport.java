package run.halo.aifoundation.provider.support;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Builds OpenAI-compatible chat options while preserving provider-specific extensions.
 */
public final class OpenAiChatOptionsSupport {

    private OpenAiChatOptionsSupport() {
    }

    public static OpenAiChatOptions buildBasic(GenerateTextRequest request,
        String providerType, ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        var builder = baseBuilder(request);
        reasoningControlOptions.apply(builder, request);
        OpenAiExtraBodyOptions.apply(builder, request, providerType, extraBodyCustomizer);
        return builder.build();
    }

    public static OpenAiChatOptions buildStructured(GenerateTextRequest request,
        String providerType, ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        var builder = baseBuilder(request);
        reasoningControlOptions.apply(builder, request);
        OpenAiExtraBodyOptions.apply(builder, request, providerType, extraBodyCustomizer);
        OpenAiStructuredOutputOptions.apply(builder, request);
        return builder.build();
    }

    public static OpenAiChatOptions buildToolCalling(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames, String providerType,
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        return buildToolCalling(request, toolCallbacks, toolNames, providerType,
            reasoningControlOptions, extraBodyCustomizer, false);
    }

    public static OpenAiChatOptions buildToolCalling(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames, String providerType,
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer,
        boolean nativeStrictToolSchemas) {
        var builder = baseBuilder(request)
            .internalToolExecutionEnabled(false)
            .toolCallbacks(toolCallbacks);
        if (nativeStrictToolSchemas) {
            OpenAiToolCallingOptions.applyNativeTools(builder, request);
        }
        reasoningControlOptions.apply(builder, request);
        OpenAiExtraBodyOptions.apply(builder, request, providerType, extraBodyCustomizer);
        OpenAiToolCallingOptions.applyToolChoice(builder, request.getToolChoice(), toolNames);
        OpenAiStructuredOutputOptions.apply(builder, request);
        return builder.build();
    }

    private static OpenAiChatOptions.Builder baseBuilder(GenerateTextRequest request) {
        return OpenAiChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .httpHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
    }
}
