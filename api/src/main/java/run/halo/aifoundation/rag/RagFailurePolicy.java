package run.halo.aifoundation.rag;

/**
 * Behavior for retrieval or reranking failures.
 */
public enum RagFailurePolicy {
    FAIL,
    USE_EMPTY_CONTEXT,
    CONTINUE_WITHOUT_CONTEXT,
    USE_RETRIEVED_ORDER
}
