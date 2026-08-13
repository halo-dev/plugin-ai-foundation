package run.halo.aifoundation.tool;

/**
 * Provider-neutral reason a model-produced tool call entered recovery.
 */
public enum ToolCallFailureKind {
    /** A known tool received malformed or schema-invalid input. */
    INVALID_INPUT,
    /** The model requested a tool absent from the current available tool set. */
    UNKNOWN_TOOL
}
