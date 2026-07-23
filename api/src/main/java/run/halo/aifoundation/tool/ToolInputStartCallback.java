package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Backpressured callback invoked before tool input start is exposed.
 */
@FunctionalInterface
public interface ToolInputStartCallback {

    Mono<Void> onInputStart(ToolInputStartContext context);
}
