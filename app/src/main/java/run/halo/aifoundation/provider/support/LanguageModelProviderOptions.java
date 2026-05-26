package run.halo.aifoundation.provider.support;

/**
 * Provider-specific switches used by the generic language model implementation.
 */
public record LanguageModelProviderOptions(
    boolean reasoningHistorySupported,
    boolean streamToolCallsForReasoning,
    ToolCallingChatOptionsFactory toolCallingChatOptionsFactory
) {
    public static LanguageModelProviderOptions defaults() {
        return new LanguageModelProviderOptions(false, false, null);
    }
}
