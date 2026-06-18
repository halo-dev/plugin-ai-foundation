package run.halo.aifoundation.rag;

/**
 * Behavior when retrieval succeeds but returns no usable context.
 */
public enum RagEmptyContextPolicy {
    SKIP_MODEL,
    CONTINUE_WITHOUT_CONTEXT
}
