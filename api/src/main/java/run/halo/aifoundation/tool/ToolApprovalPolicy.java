package run.halo.aifoundation.tool;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral approval policy for a request-scoped tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ToolApprovalPolicy {

    /**
     * Built-in approval modes.
     */
    public enum Mode {
        /**
         * Execute the tool without approval.
         */
        NEVER,
        /**
         * Always require approval before executor invocation.
         */
        ALWAYS,
        /**
         * Use {@link #predicate} to decide from validated call context.
         */
        DYNAMIC
    }

    /**
     * Approval mode. {@code null} is treated as {@link Mode#NEVER}.
     */
    private Mode mode;
    /**
     * Dynamic approval predicate. Java-only and intentionally not serialized.
     */
    private transient ToolApprovalPredicate predicate;

    public static ToolApprovalPolicy never() {
        return ToolApprovalPolicy.builder().mode(Mode.NEVER).build();
    }

    public static ToolApprovalPolicy always() {
        return ToolApprovalPolicy.builder().mode(Mode.ALWAYS).build();
    }

    public static ToolApprovalPolicy dynamic(ToolApprovalPredicate predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("tool approval predicate must not be null");
        }
        return ToolApprovalPolicy.builder()
            .mode(Mode.DYNAMIC)
            .predicate(predicate)
            .build();
    }

    public boolean requiresApproval(ToolExecutionContext context) {
        var effectiveMode = mode != null ? mode : Mode.NEVER;
        return switch (effectiveMode) {
            case NEVER -> false;
            case ALWAYS -> true;
            case DYNAMIC -> {
                if (predicate == null) {
                    throw new IllegalArgumentException(
                        "dynamic tool approval policy requires predicate");
                }
                yield predicate.needsApproval(context);
            }
        };
    }
}
