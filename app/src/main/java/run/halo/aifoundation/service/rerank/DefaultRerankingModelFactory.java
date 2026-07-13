package run.halo.aifoundation.service.rerank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.model.ModelRuntimeContextResolver;

@Component
public class DefaultRerankingModelFactory implements RerankingModelFactory {

    private final ProviderClientCache providerClientCache;
    private final RerankingModelRuntimeFactory runtimeFactory;
    private final ModelRuntimeContextResolver runtimeContextResolver;

    public DefaultRerankingModelFactory(ProviderClientCache providerClientCache,
        RerankingModelRuntimeFactory runtimeFactory) {
        this(providerClientCache, runtimeFactory,
            new ModelRuntimeContextResolver(new EffectiveParameterMappingResolver(),
                new ParameterMappingTemplateRegistry()));
    }

    @Autowired
    public DefaultRerankingModelFactory(ProviderClientCache providerClientCache,
        RerankingModelRuntimeFactory runtimeFactory,
        ModelRuntimeContextResolver runtimeContextResolver) {
        this.providerClientCache = providerClientCache;
        this.runtimeFactory = runtimeFactory;
        this.runtimeContextResolver = runtimeContextResolver;
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
        var configuration = RerankingModelRuntimeConfiguration.from(
            runtimeContextResolver.resolve(resolution));
        return runtimeFactory.create(rerankingClient, configuration);
    }
}
