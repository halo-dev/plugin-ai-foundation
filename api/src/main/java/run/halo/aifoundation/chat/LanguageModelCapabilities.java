package run.halo.aifoundation.chat;

/**
 * Read-only capability metadata exposed by a resolved language model.
 *
 * @param reasoningHistorySupported whether persisted reasoning parts can be sent back as
 *                                  model history
 */
public record LanguageModelCapabilities(boolean reasoningHistorySupported) {

    /**
     * Returns conservative default capabilities for language model implementations that do not
     * expose provider-specific behavior.
     *
     * @return default capabilities
     */
    public static LanguageModelCapabilities defaults() {
        return new LanguageModelCapabilities(false);
    }

    /**
     * Returns capabilities for a model that supports reasoning history.
     *
     * @return reasoning-history capable metadata
     */
    public static LanguageModelCapabilities supportsReasoningHistory() {
        return new LanguageModelCapabilities(true);
    }
}
