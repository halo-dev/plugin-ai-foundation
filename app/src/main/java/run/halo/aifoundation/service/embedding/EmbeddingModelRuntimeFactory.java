package run.halo.aifoundation.service.embedding;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.embedding.EmbeddingModel;

@Component
public class EmbeddingModelRuntimeFactory {

    public EmbeddingModel create(
        org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel,
        EmbeddingModelRuntimeConfiguration configuration) {
        return new EmbeddingModelImpl(springEmbeddingModel,
            EmbeddingModelRuntimeComposition.create(configuration.context().providerType(),
                configuration.maxEmbeddingsPerCall(), configuration.supportsParallelCalls(),
                configuration.providerOptions()), configuration.context());
    }
}
