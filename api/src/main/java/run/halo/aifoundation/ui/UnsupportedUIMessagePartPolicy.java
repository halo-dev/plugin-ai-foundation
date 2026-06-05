package run.halo.aifoundation.ui;

/**
 * Policy for UI message parts that cannot be converted to model message content.
 */
public enum UnsupportedUIMessagePartPolicy {
    /**
     * Ignore unsupported parts silently.
     */
    IGNORE,
    /**
     * Keep converting and return warnings for unsupported parts.
     */
    WARN,
    /**
     * Fail conversion when an unsupported part is encountered.
     */
    FAIL
}
