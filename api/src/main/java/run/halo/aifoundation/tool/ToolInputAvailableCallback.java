package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Backpressured callback invoked after normalized tool input becomes available and before any approval, external
 * handoff, or server-side execution.
 *
 * <p>Complete the returned publisher before processing continues. Failures stop generation; callbacks should observe or
 * prepare input without performing the requested tool operation. For example, an audit callback can persist the call
 * identity through a reactive service and return its completion signal.
 */
@FunctionalInterface
public interface ToolInputAvailableCallback {

    /**
     * Processes this request-scoped input event.
     *
     * @param context immutable event arguments and request metadata
     * @return completion signal; an error stops generation
     */
    Mono<Void> onInputAvailable(ToolInputAvailableContext context);
}
