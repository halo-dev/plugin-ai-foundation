package run.halo.aifoundation.service.embedding;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.service.EmbeddingModelFactory;
import run.halo.aifoundation.service.model.ModelResolution;

@Component
public class DefaultEmbeddingModelFactory implements EmbeddingModelFactory {

    private final ProviderClientCache providerClientCache;

    public DefaultEmbeddingModelFactory(ProviderClientCache providerClientCache) {
        this.providerClientCache = providerClientCache;
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
        var providerType = resolution.providerType();
        return new EmbeddingModelImpl(
            springEmbeddingModel,
            resolution.providerTypeName(),
            providerType.maxEmbeddingsPerCall(),
            providerType.supportsParallelCalls(),
            providerType.embeddingModelProviderOptions()
        );
    }
}
