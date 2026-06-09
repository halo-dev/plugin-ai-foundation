package run.halo.aifoundation.provider.support.openai;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

/**
 * Builds OpenAI-compatible chat options while preserving provider-specific extensions.
 */
public final class OpenAiChatOptionsSupport {

    private OpenAiChatOptionsSupport() {
    }

    public static OpenAiCompatibleChatOptions buildBasic(GenerateTextRequest request,
        String providerType, ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        var builder = baseBuilder(request);
        reasoningControlOptions.apply(builder, request);
        OpenAiExtraBodyOptions.apply(builder, request, providerType, extraBodyCustomizer);
        return builder.build();
    }

    public static OpenAiCompatibleChatOptions buildStructured(GenerateTextRequest request,
        String providerType, ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        var builder = baseBuilder(request);
        reasoningControlOptions.apply(builder, request);
        OpenAiExtraBodyOptions.apply(builder, request, providerType, extraBodyCustomizer);
        OpenAiStructuredOutputOptions.apply(builder, request);
        return builder.build();
    }

    public static OpenAiCompatibleChatOptions buildToolCalling(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames, String providerType,
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        return buildToolCalling(request, toolCallbacks, toolNames, providerType,
            reasoningControlOptions, extraBodyCustomizer, false);
    }

    public static OpenAiCompatibleChatOptions buildToolCalling(GenerateTextRequest request,
        java.util.List<ToolCallback> toolCallbacks, Set<String> toolNames, String providerType,
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer,
        boolean nativeStrictToolSchemas) {
        var builder = baseBuilder(request)
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

    private static OpenAiCompatibleChatOptions.Builder baseBuilder(GenerateTextRequest request) {
        return OpenAiCompatibleChatOptions.builder()
            .temperature(request.getTemperature())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .maxTokens(request.getMaxOutputTokens())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .customHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
    }
}
