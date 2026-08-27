package run.halo.aifoundation.provider.doubao;

import java.util.List;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

final class DouBaoEmbeddingOptionsFactory {

    private DouBaoEmbeddingOptionsFactory() {
    }

    static DouBaoEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return DouBaoEmbeddingOptions.builder().build();
        }
        return DouBaoEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .instructions(request.getInstructions())
            .includeSparseEmbedding(Boolean.TRUE.equals(request.getIncludeSparseEmbedding()))
            .includeModalityEmbeddings(
                Boolean.TRUE.equals(request.getIncludeModalityEmbeddings()))
            .build();
    }
}
