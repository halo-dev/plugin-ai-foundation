package run.halo.aifoundation.chat;

/**
 * Provider-neutral reason why a generation step or final response stopped.
 *
 * <p>The values intentionally mirror the common provider finish reasons finish reasons while hiding
 * provider-specific names. Providers that return an unknown value are normalized to
 * {@link #OTHER} or {@link #UNKNOWN} depending on whether the provider reported a concrete reason.
 */
public enum FinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    ERROR,
    OTHER,
    UNKNOWN
}
