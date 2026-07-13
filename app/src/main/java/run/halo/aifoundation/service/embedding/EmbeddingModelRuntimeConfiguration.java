package run.halo.aifoundation.service.embedding;

import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

public record EmbeddingModelRuntimeConfiguration(
    ModelRuntimeContext context,
    int maxEmbeddingsPerCall,
    boolean supportsParallelCalls,
    EmbeddingModelProviderOptions providerOptions
) {

    public static EmbeddingModelRuntimeConfiguration from(ModelRuntimeContext context) {
        var provider = context.providerDefinition();
        return new EmbeddingModelRuntimeConfiguration(context, provider.maxEmbeddingsPerCall(),
            provider.supportsParallelCalls(), provider.embeddingModelProviderOptions());
    }
}
