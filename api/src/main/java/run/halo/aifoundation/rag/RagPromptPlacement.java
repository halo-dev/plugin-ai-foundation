package run.halo.aifoundation.rag;

/**
 * Where packed retrieval context is injected into the model input.
 */
public enum RagPromptPlacement {
    LAST_USER_MESSAGE,
    SYSTEM,
    NEW_USER_MESSAGE
}
