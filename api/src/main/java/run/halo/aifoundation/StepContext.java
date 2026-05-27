package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable context passed to step-control callbacks.
 */
@Value
@Builder
public class StepContext {
    /**
     * Zero-based step index being prepared or just completed.
     */
    Integer stepIndex;
    /**
     * Step that just completed when evaluating a stop condition. Null while preparing a step.
     */
    GenerationStep step;
    /**
     * Steps completed before the current callback.
     */
    List<GenerationStep> steps;
    /**
     * Provider-neutral messages planned for the current step.
     */
    List<ModelMessage> messages;
    /**
     * Request-scoped tools available before active-tool filtering.
     */
    List<ToolDefinition> tools;
    /**
     * Current stop condition.
     */
    transient StopCondition stopWhen;
    /**
     * Request provider options visible to this step.
     */
    Map<String, Map<String, Object>> providerOptions;
}
