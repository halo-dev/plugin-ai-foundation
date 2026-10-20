package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Request-scoped callback that can repair invalid tool call input before tool processing.
 *
 * <p>The callback is invoked at most once for a known internal or external tool when model output fails input schema
 * validation. Repaired input is validated again before availability, approval, external handoff, or execution.
 * Returning {@link ToolCallRepairResult#unrepaired()} keeps the original validation failure.
 */
@FunctionalInterface
public interface ToolCallRepairCallback {

    /**
     * Proposes corrected arguments for a known tool whose input failed parsing or validation.
     *
     * @param context original call, matching definition, and validation details
     * @return a corrected call or {@link ToolCallRepairResult#unrepaired()}
     */
    Mono<ToolCallRepairResult> repair(ToolCallRepairContext context);
}
