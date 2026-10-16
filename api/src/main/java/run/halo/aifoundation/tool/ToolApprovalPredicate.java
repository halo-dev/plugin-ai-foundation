package run.halo.aifoundation.tool;

/**
 * Decides whether a validated tool call requires explicit approval before execution.
 *
 * <p>Return a decision from the supplied arguments without performing the requested operation. For example,
 * {@code context -> Boolean.TRUE.equals(context.getInput().get("publish"))} requires confirmation only for publishing.
 * Resource authorization still runs separately.
 */
@FunctionalInterface
public interface ToolApprovalPredicate {

    /**
     * Returns {@code true} when the tool call should be returned as an approval request instead of executed
     * immediately.
     *
     * @param context validated arguments and trusted request metadata
     * @return whether confirmation is required
     */
    boolean needsApproval(ToolExecutionContext context);
}
