package run.halo.aifoundation.chat;

import run.halo.aifoundation.capability.ModelCapabilities;

/**
 * Read-only capability metadata exposed by a resolved language model.
 *
 * @param reasoningHistorySupported whether persisted reasoning parts can be sent back as
 *                                  model history
 * @param modelCapabilities fine-grained effective capability snapshot for the resolved model
 */
public record LanguageModelCapabilities(boolean reasoningHistorySupported,
                                        ModelCapabilities modelCapabilities) {

    public LanguageModelCapabilities {
        if (modelCapabilities == null) {
            modelCapabilities = ModelCapabilities.empty();
        }
    }

    public LanguageModelCapabilities(boolean reasoningHistorySupported) {
        this(reasoningHistorySupported, ModelCapabilities.empty());
    }

    /**
     * Returns conservative default capabilities for language model implementations that do not
     * expose provider-specific behavior.
     *
     * @return default capabilities
     */
    public static LanguageModelCapabilities defaults() {
        return new LanguageModelCapabilities(false, ModelCapabilities.empty());
    }

    /**
     * Returns capabilities for a model that supports reasoning history.
     *
     * @return reasoning-history capable metadata
     */
    public static LanguageModelCapabilities supportsReasoningHistory() {
        return new LanguageModelCapabilities(true, ModelCapabilities.empty());
    }

    /**
     * Creates language model capability metadata.
     *
     * @param reasoningHistorySupported whether persisted assistant reasoning can be replayed as
     *                                  model history
     * @param modelCapabilities fine-grained effective model capability snapshot
     * @return language model capability metadata
     */
    public static LanguageModelCapabilities of(boolean reasoningHistorySupported,
        ModelCapabilities modelCapabilities) {
        return new LanguageModelCapabilities(reasoningHistorySupported, modelCapabilities);
    }
}
