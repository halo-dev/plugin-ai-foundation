package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Backpressured callback invoked after normalized tool input becomes available and before any
 * approval, external handoff, or server-side execution.
 */
@FunctionalInterface
public interface ToolInputAvailableCallback {

    Mono<Void> onInputAvailable(ToolInputAvailableContext context);
}
