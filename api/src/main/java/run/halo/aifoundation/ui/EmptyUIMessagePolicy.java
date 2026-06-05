package run.halo.aifoundation.ui;

/**
 * Policy for UI messages that produce no model message content during conversion.
 */
public enum EmptyUIMessagePolicy {
    /**
     * Skip empty UI messages and emit a conversion warning.
     */
    SKIP,
    /**
     * Fail conversion when a UI message produces no model content.
     */
    FAIL
}
