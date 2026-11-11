package run.halo.aifoundation.decision;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.DecisionCapability;

/** Provider-neutral inference for closed decisions. Callers own thresholds and actions. */
public interface DecisionModel {
    Mono<DecisionResponse> decide(DecisionRequest request);

    /** Read-only capability snapshot, or null when unavailable. */
    default DecisionCapability capabilities() {
        return null;
    }
}
