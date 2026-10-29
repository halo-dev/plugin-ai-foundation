package run.halo.aifoundation.service.decision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.decision.DecisionModel;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;

@Component
@RequiredArgsConstructor
public class DecisionModelFactory {
    private final ProviderClientCache providerClientCache;
    private final UsageExecutionObserver observer;

    public DecisionModel create(ModelResolution resolution) {
        var client = providerClientCache.getOrCreateDecisionClient(resolution.provider(),
            resolution.apiKey(), ProviderModelRef.from(resolution.model()));
        if (client == null) {
            throw new ModelNotFoundException("Provider does not support decision inference");
        }
        return new DecisionModelImpl(client, observer, resolution.providerType().decisionCapabilities());
    }
}
