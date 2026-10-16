package run.halo.aifoundation.tool;

/**
 * Determines how shared tool resolution handles a caller lacking declared permissions.
 *
 * <p>{@link #FILTER_ON_RESOLVE} is the default: the model never receives unauthorized tools. Choose
 * {@link #DENY_ON_EXECUTE} when the model should know an operation exists and report that it cannot execute it. Both
 * modes recheck current permissions before invoking the executor.
 *
 * @see ToolRegistration#getPermissionDeniedBehavior()
 */
public enum ToolPermissionDeniedBehavior {
    /** Omit unauthorized tools when resolving tools for a model. */
    FILTER_ON_RESOLVE,
    /** Keep the tool visible to the model, and reject unauthorized execution. */
    DENY_ON_EXECUTE
}
