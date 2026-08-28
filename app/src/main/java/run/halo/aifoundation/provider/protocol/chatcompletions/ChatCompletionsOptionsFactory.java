package run.halo.aifoundation.provider.protocol.chatcompletions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

/** Builds request options for one provider's Chat Completions contract. */
public final class ChatCompletionsOptionsFactory {

    private final String providerType;
    private final ReasoningControlOptions reasoningControlOptions;
    private final BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer;
    private final boolean nativeStrictToolSchemas;
    private final StructuredOutputSupport structuredOutputSupport;

    private ChatCompletionsOptionsFactory(Builder builder) {
        providerType = builder.providerType;
        reasoningControlOptions = builder.reasoningControlOptions;
        extraBodyCustomizer = builder.extraBodyCustomizer;
        nativeStrictToolSchemas = builder.nativeStrictToolSchemas;
        structuredOutputSupport = builder.structuredOutputSupport;
    }

    public static Builder builder(String providerType,
        ReasoningControlOptions reasoningControlOptions) {
        return new Builder(providerType, reasoningControlOptions);
    }

    public ChatCompletionsOptions basic(GenerateTextRequest request) {
        var builder = baseBuilder(request);
        applyCommonOptions(builder, request);
        return builder.build();
    }

    public ChatCompletionsOptions structured(GenerateTextRequest request) {
        var builder = baseBuilder(request);
        applyCommonOptions(builder, request);
        ChatCompletionsStructuredOutputOptions.apply(builder, request, structuredOutputSupport);
        return builder.build();
    }

    public ChatCompletionsOptions toolCalling(GenerateTextRequest request,
        List<ToolCallback> toolCallbacks, Set<String> toolNames) {
        var builder = baseBuilder(request).toolCallbacks(toolCallbacks);
        if (nativeStrictToolSchemas) {
            ChatCompletionsToolCallingOptions.applyNativeTools(builder, request);
        }
        applyCommonOptions(builder, request);
        ChatCompletionsToolCallingOptions.applyToolChoice(
            builder, request.getToolChoice(), toolNames);
        ChatCompletionsStructuredOutputOptions.apply(builder, request, structuredOutputSupport);
        return builder.build();
    }

    private void applyCommonOptions(ChatCompletionsOptions.Builder builder,
        GenerateTextRequest request) {
        reasoningControlOptions.validate(providerType, request);
        ChatCompletionsExtraBodyOptions.apply(builder, request, extraBodyCustomizer);
    }

    private static ChatCompletionsOptions.Builder baseBuilder(GenerateTextRequest request) {
        return ChatCompletionsOptions.builder()
            .temperature(request.getTemperature())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .maxTokens(request.getMaxOutputTokens())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .logprobs(logprobs(request))
            .topLogprobs(request.getTopLogprobs())
            .parallelToolCalls(request.getParallelToolCalls())
            .customHeaders(headers(request));
    }

    private static Boolean logprobs(GenerateTextRequest request) {
        if (request.getLogprobs() != null) {
            return request.getLogprobs();
        }
        if (request.getTopLogprobs() == null) {
            return null;
        }
        return true;
    }

    private static Map<String, String> headers(GenerateTextRequest request) {
        if (request.getHeaders() == null) {
            return Map.of();
        }
        return request.getHeaders();
    }

    public static final class Builder {

        private final String providerType;
        private final ReasoningControlOptions reasoningControlOptions;
        private BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer;
        private boolean nativeStrictToolSchemas;
        private StructuredOutputSupport structuredOutputSupport =
            StructuredOutputSupport.JSON_SCHEMA;

        private Builder(String providerType, ReasoningControlOptions reasoningControlOptions) {
            this.providerType = requireProviderType(providerType);
            this.reasoningControlOptions = Objects.requireNonNull(
                reasoningControlOptions, "Reasoning control options must not be null");
        }

        private static String requireProviderType(String providerType) {
            if (providerType == null) {
                throw new IllegalArgumentException("Provider type must not be blank");
            }
            if (providerType.isBlank()) {
                throw new IllegalArgumentException("Provider type must not be blank");
            }
            return providerType;
        }

        public Builder extraBodyCustomizer(
            BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
            this.extraBodyCustomizer = extraBodyCustomizer;
            return this;
        }

        public Builder nativeStrictToolSchemas(boolean nativeStrictToolSchemas) {
            this.nativeStrictToolSchemas = nativeStrictToolSchemas;
            return this;
        }

        public Builder structuredOutputSupport(
            StructuredOutputSupport structuredOutputSupport) {
            this.structuredOutputSupport = Objects.requireNonNull(
                structuredOutputSupport, "Structured output support must not be null");
            return this;
        }

        public ChatCompletionsOptionsFactory build() {
            return new ChatCompletionsOptionsFactory(this);
        }
    }
}
