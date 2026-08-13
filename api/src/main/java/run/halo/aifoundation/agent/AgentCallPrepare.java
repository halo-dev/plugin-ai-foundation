package run.halo.aifoundation.agent;

import reactor.core.publisher.Mono;

/**
 * Asynchronously prepares one effective model and request for an agent call.
 *
 * @param <O> call options type
 */
@FunctionalInterface
public interface AgentCallPrepare<O> {

    /**
     * Prepares the current call. The callback runs exactly once per subscribed agent call.
     */
    Mono<PreparedAgentCall> prepare(AgentCallPrepareContext<O> context);
}
