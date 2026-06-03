package run.halo.aifoundation.tool;

/**
 * Decides whether a validated tool call requires explicit approval before execution.
 */
@FunctionalInterface
public interface ToolApprovalPredicate {

    /**
     * Returns {@code true} when the tool call should be returned as an approval request instead of
     * executed immediately.
     */
    boolean needsApproval(ToolExecutionContext context);
}
