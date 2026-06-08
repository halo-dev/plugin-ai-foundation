package run.halo.aifoundation.ui;

/**
 * Role of a persisted UI message in a conversation.
 */
public enum UIMessageRole {
    /**
     * System instruction message.
     */
    SYSTEM,
    /**
     * End-user message.
     */
    USER,
    /**
     * Assistant response message.
     */
    ASSISTANT
}
