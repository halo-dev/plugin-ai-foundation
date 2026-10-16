package run.halo.aifoundation.tool;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral approval policy for a request-scoped tool.
 *
 * <p>Approval is evaluated after input validation. It asks the caller to confirm an operation; it does not grant Halo
 * permissions. Choose a constant policy or a predicate based on validated arguments, for example:
 *
 * <pre>{@code
 * ToolApprovalPolicy policy = ToolApprovalPolicy.dynamic(context ->
 *     Boolean.TRUE.equals(context.getInput().get("publish")));
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ToolApprovalPolicy {

    /** Built-in approval modes. */
    public enum Mode {
        /** Execute the tool without approval. */
        NEVER,
        /** Always require approval before executor invocation. */
        ALWAYS,
        /** Use {@link ToolApprovalPolicy#predicate} to decide from validated call context. */
        DYNAMIC
    }

    /** Approval mode. {@code null} is treated as {@link Mode#NEVER}. */
    private Mode mode;
    /** Dynamic approval predicate. Java-only and intentionally not serialized. */
    private transient ToolApprovalPredicate predicate;

    /**
     * Creates a policy that executes without requesting confirmation.
     *
     * @return a policy using {@link Mode#NEVER}
     */
    public static ToolApprovalPolicy never() {
        return ToolApprovalPolicy.builder().mode(Mode.NEVER).build();
    }

    /**
     * Creates a policy that requests confirmation for every validated call.
     *
     * @return a policy using {@link Mode#ALWAYS}
     */
    public static ToolApprovalPolicy always() {
        return ToolApprovalPolicy.builder().mode(Mode.ALWAYS).build();
    }

    /**
     * Creates a policy that evaluates validated arguments before requesting confirmation.
     *
     * @param predicate decision callback, never {@code null}
     * @return a policy using {@link Mode#DYNAMIC}
     * @throws IllegalArgumentException if the predicate is {@code null}
     */
    public static ToolApprovalPolicy dynamic(ToolApprovalPredicate predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("tool approval predicate must not be null");
        }
        return ToolApprovalPolicy.builder()
                .mode(Mode.DYNAMIC)
                .predicate(predicate)
                .build();
    }

    /**
     * Evaluates this policy for the current validated tool call.
     *
     * @param context current execution arguments and request metadata
     * @return whether the caller must approve execution
     * @throws IllegalArgumentException if a dynamic policy has no predicate
     */
    public boolean requiresApproval(ToolExecutionContext context) {
        var effectiveMode = mode != null ? mode : Mode.NEVER;
        return switch (effectiveMode) {
            case NEVER -> false;
            case ALWAYS -> true;
            case DYNAMIC -> {
                if (predicate == null) {
                    throw new IllegalArgumentException("dynamic tool approval policy requires predicate");
                }
                yield predicate.needsApproval(context);
            }
        };
    }
}
