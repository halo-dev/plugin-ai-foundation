package run.halo.aifoundation.provider.support;

/**
 * Provider-specific switches used by the generic language model implementation.
 */
public record LanguageModelProviderOptions(
    boolean reasoningHistorySupported,
    boolean streamToolCallsForReasoning,
    ChatOptionsFactory chatOptionsFactory,
    ToolCallingChatOptionsFactory toolCallingChatOptionsFactory,
    StructuredOutputChatOptionsFactory structuredOutputChatOptionsFactory,
    ReasoningControlOptions reasoningControlOptions
) {
    public LanguageModelProviderOptions(boolean reasoningHistorySupported,
        boolean streamToolCallsForReasoning,
        ToolCallingChatOptionsFactory toolCallingChatOptionsFactory,
        StructuredOutputChatOptionsFactory structuredOutputChatOptionsFactory) {
        this(reasoningHistorySupported, streamToolCallsForReasoning, null,
            toolCallingChatOptionsFactory, structuredOutputChatOptionsFactory,
            ReasoningControlOptions.unsupported());
    }

    public static LanguageModelProviderOptions defaults() {
        return new LanguageModelProviderOptions(false, false, null, null, null,
            ReasoningControlOptions.unsupported());
    }
}
