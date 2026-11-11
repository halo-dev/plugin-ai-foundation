package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Request-scoped callback that can recover invalid or unknown tool calls before tool processing.
 *
 * <p>The callback can recover a known internal or external tool when model output fails input schema validation, or
 * when the model selects an unknown tool name. Repaired calls are fully validated again before availability, approval,
 * external handoff, or execution. Returning {@link ToolCallRepairResult#unrepaired()} keeps the original safe failure.
 */
@FunctionalInterface
public interface ToolCallRepairCallback {

    Mono<ToolCallRepairResult> repair(ToolCallRepairContext context);
}
