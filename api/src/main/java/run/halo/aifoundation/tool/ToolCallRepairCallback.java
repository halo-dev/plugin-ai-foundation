package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Request-scoped callback that can repair invalid tool call input before server-side execution.
 *
 * <p>The callback is invoked only for known tools with a server-side executor when the model output
 * fails the tool input schema validation. Returning {@link ToolCallRepairResult#unrepaired()} keeps
 * the original validation failure.
 */
@FunctionalInterface
public interface ToolCallRepairCallback {

    Mono<ToolCallRepairResult> repair(ToolCallRepairContext context);
}
