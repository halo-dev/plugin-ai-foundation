package run.halo.aifoundation.lifecycle;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerationStep;

/**
 * Lifecycle event emitted when text generation fails before a successful finish event.
 *
 * <p>The event includes the failed step index when known, all completed steps, and the original
 * request metadata/context for logging. Callback implementations should return quickly and avoid
 * throwing; callback failures are converted to warnings where possible.
 */
@Value
@Builder
public class GenerationErrorEvent {
    /**
     * Failure that stopped generation.
     */
    Throwable error;
    /**
     * Step index active when the failure occurred, when known.
     */
    Integer stepIndex;
    /**
     * Steps completed before the failure.
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
