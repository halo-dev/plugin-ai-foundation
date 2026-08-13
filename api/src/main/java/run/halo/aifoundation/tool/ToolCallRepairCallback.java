package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Request-scoped callback that can recover invalid or unknown tool calls before tool processing.
 *
 * <p>Repaired calls are fully validated again before availability, approval, external handoff, or
 * execution. Returning {@link ToolCallRepairResult#unrepaired()} keeps the original safe failure.
 */
@FunctionalInterface
public interface ToolCallRepairCallback {

    Mono<ToolCallRepairResult> repair(ToolCallRepairContext context);
}
