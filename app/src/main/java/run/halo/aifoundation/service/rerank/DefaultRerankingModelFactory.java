package run.halo.aifoundation.service.rerank;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.model.ModelResolution;

@Component
public class DefaultRerankingModelFactory implements RerankingModelFactory {

    private final ProviderClientCache providerClientCache;
    private final RerankingModelRuntimeFactory runtimeFactory;

    public DefaultRerankingModelFactory(ProviderClientCache providerClientCache,
        RerankingModelRuntimeFactory runtimeFactory) {
        this.providerClientCache = providerClientCache;
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public RerankingModel create(ModelResolution resolution) {
        var rerankingClient = providerClientCache.getOrCreateRerankingClient(
            resolution.provider(), resolution.apiKey(), resolution.modelId());
        if (rerankingClient == null) {
            throw new ModelNotFoundException(
                "Provider '" + resolution.provider().getMetadata().getName()
                    + "' does not support reranking");
        }
        var providerType = resolution.providerType();
        return runtimeFactory.create(
            rerankingClient,
            resolution.providerTypeName(),
            providerType.rerankingModelProviderOptions()
        );
    }
}
