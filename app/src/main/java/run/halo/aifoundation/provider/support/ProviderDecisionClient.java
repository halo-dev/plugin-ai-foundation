package run.halo.aifoundation.provider.support;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;

/** Low-level provider adapter for typed decisions. */
@FunctionalInterface
public interface ProviderDecisionClient {
    Mono<DecisionResponse> decide(DecisionRequest request);
}
