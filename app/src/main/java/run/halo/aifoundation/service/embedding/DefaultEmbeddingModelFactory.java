package run.halo.aifoundation.service.embedding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.service.EmbeddingModelFactory;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.model.ModelRuntimeContextResolver;

@Component
public class DefaultEmbeddingModelFactory implements EmbeddingModelFactory {

    private final ProviderClientCache providerClientCache;
    private final EmbeddingModelRuntimeFactory runtimeFactory;
    private final ModelRuntimeContextResolver runtimeContextResolver;

    public DefaultEmbeddingModelFactory(ProviderClientCache providerClientCache,
        EmbeddingModelRuntimeFactory runtimeFactory) {
        this(providerClientCache, runtimeFactory,
            new ModelRuntimeContextResolver(new EffectiveParameterMappingResolver(),
                new ParameterMappingTemplateRegistry()));
    }

    @Autowired
    public DefaultEmbeddingModelFactory(ProviderClientCache providerClientCache,
        EmbeddingModelRuntimeFactory runtimeFactory,
        ModelRuntimeContextResolver runtimeContextResolver) {
        this.providerClientCache = providerClientCache;
        this.runtimeFactory = runtimeFactory;
        this.runtimeContextResolver = runtimeContextResolver;
    }

    @Override
    public EmbeddingModel create(ModelResolution resolution) {
        var springEmbeddingModel = providerClientCache.getOrCreateEmbeddingModel(
            resolution.provider(), resolution.apiKey(), resolution.modelId());
        if (springEmbeddingModel == null) {
            throw new ModelNotFoundException(
                "Provider '" + resolution.provider().getMetadata().getName()
                    + "' does not support embeddings");
        }
        var configuration = EmbeddingModelRuntimeConfiguration.from(
            runtimeContextResolver.resolve(resolution));
        return runtimeFactory.create(springEmbeddingModel, configuration);
    }
}
