package run.halo.aifoundation.provider.support;

/**
 * Provider-specific switches used by the generic language model implementation.
 */
public record LanguageModelProviderOptions(
    boolean reasoningHistorySupported,
    boolean streamToolCallsForReasoning,
    boolean requestHeadersSupported,
    boolean seedSupported,
    ChatOptionsFactory chatOptionsFactory,
    ToolCallingChatOptionsFactory toolCallingChatOptionsFactory,
    StructuredOutputChatOptionsFactory structuredOutputChatOptionsFactory,
    ReasoningControlOptions reasoningControlOptions,
    AssistantReasoningContentExtractor reasoningContentExtractor
) {
    public static LanguageModelProviderOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean reasoningHistorySupported;
        private boolean streamToolCallsForReasoning;
        private boolean requestHeadersSupported;
        private boolean seedSupported;
        private ChatOptionsFactory chatOptionsFactory;
        private ToolCallingChatOptionsFactory toolCallingChatOptionsFactory;
        private StructuredOutputChatOptionsFactory structuredOutputChatOptionsFactory;
        private ReasoningControlOptions reasoningControlOptions = ReasoningControlOptions.unsupported();
        private AssistantReasoningContentExtractor reasoningContentExtractor;

        private Builder() {
        }

        public Builder reasoningHistorySupported(boolean reasoningHistorySupported) {
            this.reasoningHistorySupported = reasoningHistorySupported;
            return this;
        }

        public Builder streamToolCallsForReasoning(boolean streamToolCallsForReasoning) {
            this.streamToolCallsForReasoning = streamToolCallsForReasoning;
            return this;
        }

        public Builder requestHeadersSupported(boolean requestHeadersSupported) {
            this.requestHeadersSupported = requestHeadersSupported;
            return this;
        }

        public Builder seedSupported(boolean seedSupported) {
            this.seedSupported = seedSupported;
            return this;
        }

        public Builder chatOptionsFactory(ChatOptionsFactory chatOptionsFactory) {
            this.chatOptionsFactory = chatOptionsFactory;
            return this;
        }

        public Builder toolCallingChatOptionsFactory(
            ToolCallingChatOptionsFactory toolCallingChatOptionsFactory) {
            this.toolCallingChatOptionsFactory = toolCallingChatOptionsFactory;
            return this;
        }

        public Builder structuredOutputChatOptionsFactory(
            StructuredOutputChatOptionsFactory structuredOutputChatOptionsFactory) {
            this.structuredOutputChatOptionsFactory = structuredOutputChatOptionsFactory;
            return this;
        }

        public Builder reasoningControlOptions(ReasoningControlOptions reasoningControlOptions) {
            this.reasoningControlOptions = reasoningControlOptions != null
                ? reasoningControlOptions
                : ReasoningControlOptions.unsupported();
            return this;
        }

        public Builder reasoningContentExtractor(
            AssistantReasoningContentExtractor reasoningContentExtractor) {
            this.reasoningContentExtractor = reasoningContentExtractor;
            return this;
        }

        public LanguageModelProviderOptions build() {
            return new LanguageModelProviderOptions(
                reasoningHistorySupported,
                streamToolCallsForReasoning,
                requestHeadersSupported,
                seedSupported,
                chatOptionsFactory,
                toolCallingChatOptionsFactory,
                structuredOutputChatOptionsFactory,
                reasoningControlOptions,
                reasoningContentExtractor
            );
        }
    }
}
