package run.halo.aifoundation.service.embedding;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;

@Component
public class EmbeddingModelRuntimeFactory {

    private final UsageExecutionObserver usageExecutionObserver;

    public EmbeddingModelRuntimeFactory(UsageExecutionObserver usageExecutionObserver) {
        this.usageExecutionObserver = usageExecutionObserver;
    }

    public EmbeddingModel create(
        org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel,
        EmbeddingModelRuntimeConfiguration configuration) {
        return new EmbeddingModelImpl(springEmbeddingModel,
            EmbeddingModelRuntimeComposition.create(configuration.context().providerType(),
                configuration.maxEmbeddingsPerCall(), configuration.supportsParallelCalls(),
                configuration.providerOptions()), configuration.context(), usageExecutionObserver);
    }
}
