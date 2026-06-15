package run.halo.aifoundation.ui;

/**
 * Lifecycle state of a dynamic tool UI message part.
 */
public enum ToolPartState {
    /**
     * Tool input is still streaming and cannot be executed yet.
     */
    INPUT_STREAMING("input-streaming"),

    /**
     * Tool input is complete and available to a client-side or external executor.
     */
    INPUT_AVAILABLE("input-available"),

    /**
     * Tool execution is waiting for caller approval.
     */
    APPROVAL_REQUESTED("approval-requested"),

    /**
     * Caller has approved or denied a pending tool approval request.
     */
    APPROVAL_RESPONDED("approval-responded"),

    /**
     * Tool execution completed with an output payload.
     */
    OUTPUT_AVAILABLE("output-available"),

    /**
     * Tool execution was denied before execution.
     */
    OUTPUT_DENIED("output-denied"),

    /**
     * Tool execution completed with an error.
     */
    OUTPUT_ERROR("output-error");

    private final String value;

    ToolPartState(String value) {
        this.value = value;
    }

    /**
     * Returns the wire value.
     *
     * @return wire value
     */
    public String value() {
        return value;
    }

    /**
     * Parses a wire value.
     *
     * @param value wire value
     * @return matching state
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ToolPartState fromValue(String value) {
        for (var state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown tool part state: " + value);
    }
}
