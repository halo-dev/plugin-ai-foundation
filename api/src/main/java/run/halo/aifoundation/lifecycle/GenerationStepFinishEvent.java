package run.halo.aifoundation.lifecycle;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerationStep;

/**
 * Lifecycle event emitted after each provider generation step completes.
 *
 * <p>A step can finish with final text, a tool-call request, a length stop, or an error reason. The
 * {@link #getSteps()} list includes all steps completed so far, including {@link #getStep()}.
 */
@Value
@Builder
public class GenerationStepFinishEvent {
    /**
     * Zero-based generation step index.
     */
    int stepIndex;
    /**
     * Step result that just finished.
     */
    GenerationStep step;
    /**
     * Steps completed so far, including {@link #step}.
     */
    List<GenerationStep> steps;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
