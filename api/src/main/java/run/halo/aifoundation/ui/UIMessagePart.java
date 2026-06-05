package run.halo.aifoundation.ui;

/**
 * One persisted part of a Halo UI message.
 *
 * <p>Parts are the stable content stored in {@link UIMessage#parts()}. Runtime-only
 * stream chunks such as tool input deltas, finish events, errors, and aborts are
 * not represented as parts.
 */
public sealed interface UIMessagePart permits TextPart, ReasoningPart, DataPart, SourceUrlPart,
    FilePart, ToolCallPart, ToolResultPart, ToolErrorPart, ToolApprovalRequestPart,
    ToolApprovalResponsePart {

    /**
     * Stable discriminator used by serializers and callers.
     *
     * @return part protocol type
     */
    String type();

    /**
     * JavaBean accessor for serializers that do not inspect plain interface methods.
     *
     * @return part protocol type
     */
    default String getType() {
        return type();
    }
}
