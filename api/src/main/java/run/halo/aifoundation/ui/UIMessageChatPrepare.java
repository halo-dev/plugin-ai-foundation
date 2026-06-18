package run.halo.aifoundation.ui;

import reactor.core.publisher.Mono;

/**
 * Async hook for preparing a UI message chat model request before execution.
 */
@FunctionalInterface
public interface UIMessageChatPrepare<M> {

    Mono<Void> prepare(UIMessageChatPrepareContext<M> context);
}
