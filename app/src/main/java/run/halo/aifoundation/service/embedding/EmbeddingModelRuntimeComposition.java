package run.halo.aifoundation.service.embedding;

import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

public record EmbeddingModelRuntimeComposition(
    String providerType,
    int maxEmbeddingsPerCall,
    boolean supportsParallelCalls,
    EmbeddingModelProviderOptions providerOptions,
    EmbeddingBatchPlanner batchPlanner,
    EmbeddingResponseAggregator responseAggregator
) {
    public static EmbeddingModelRuntimeComposition create(String providerType,
        int maxEmbeddingsPerCall, boolean supportsParallelCalls,
        EmbeddingModelProviderOptions providerOptions) {
        var resolvedProviderOptions = providerOptions != null
            ? providerOptions
            : EmbeddingModelProviderOptions.defaults(providerType);
        return new EmbeddingModelRuntimeComposition(
            providerType,
            maxEmbeddingsPerCall,
            supportsParallelCalls,
            resolvedProviderOptions,
            new EmbeddingBatchPlanner(maxEmbeddingsPerCall, supportsParallelCalls),
            new EmbeddingResponseAggregator(providerType)
        );
    }
}
