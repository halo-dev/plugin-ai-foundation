package run.halo.aifoundation;

public enum FinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    ERROR,
    OTHER,
    UNKNOWN
}
