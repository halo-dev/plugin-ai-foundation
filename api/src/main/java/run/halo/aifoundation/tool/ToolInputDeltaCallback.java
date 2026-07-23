package run.halo.aifoundation.tool;

import reactor.core.publisher.Mono;

/**
 * Backpressured callback invoked before one provider-native tool input delta is exposed.
 */
@FunctionalInterface
public interface ToolInputDeltaCallback {

    Mono<Void> onInputDelta(ToolInputDeltaContext context);
}
