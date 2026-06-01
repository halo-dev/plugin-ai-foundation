package run.halo.aifoundation.message;

/**
 * Role assigned to a {@link ModelMessage}.
 *
 * <p>The roles match the provider-neutral chat model convention: system instructions, user input,
 * assistant responses, and tool result messages.
 */
public enum ModelMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
