package run.halo.aifoundation.service.embedding;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

@Component
public class EmbeddingModelRuntimeFactory {

    public EmbeddingModel create(
        org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel,
        String providerType,
        int maxEmbeddingsPerCall,
        boolean supportsParallelCalls,
        EmbeddingModelProviderOptions providerOptions) {
        return new EmbeddingModelImpl(springEmbeddingModel, compose(providerType,
            maxEmbeddingsPerCall, supportsParallelCalls, providerOptions));
    }

    EmbeddingModelRuntimeComposition compose(String providerType, int maxEmbeddingsPerCall,
        boolean supportsParallelCalls, EmbeddingModelProviderOptions providerOptions) {
        return EmbeddingModelRuntimeComposition.create(providerType, maxEmbeddingsPerCall,
            supportsParallelCalls, providerOptions);
    }
}
