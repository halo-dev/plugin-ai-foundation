package run.halo.aifoundation.chat;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.tool.ToolDefinition;

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
    /** Maximum output-token setting resolved for the current step. */
    Integer maxOutputTokens;
    /** Sampling temperature resolved for the current step. */
    Double temperature;
    /** Nucleus-sampling value resolved for the current step. */
    Double topP;
    /** Top-k sampling value resolved for the current step. */
    Integer topK;
    /** Minimum-probability sampling threshold resolved for the current step. */
    Double minP;
    /** Presence penalty resolved for the current step. */
    Double presencePenalty;
    /** Frequency penalty resolved for the current step. */
    Double frequencyPenalty;
    /** Repetition penalty resolved for the current step. */
    Double repetitionPenalty;
    /** Whether token log probabilities are enabled for the current step. */
    Boolean logprobs;
    /** Number of top token log probabilities requested for the current step. */
    Integer topLogprobs;
    /** Whether parallel tool calls are enabled for the current step. */
    Boolean parallelToolCalls;
    /** Stop sequences resolved for the current step. */
    List<String> stopSequences;
    /**
     * Current deterministic sampling seed, if one was configured.
     */
    Integer seed;
    /**
     * Current retry attempts for retryable non-streaming provider calls.
     */
    Integer maxRetries;
}
