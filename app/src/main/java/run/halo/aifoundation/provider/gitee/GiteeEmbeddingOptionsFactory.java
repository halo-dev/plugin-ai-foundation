package run.halo.aifoundation.provider.gitee;

import java.util.LinkedHashMap;
import java.util.List;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

final class GiteeEmbeddingOptionsFactory {

    private GiteeEmbeddingOptionsFactory() {
    }

    static GiteeEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return GiteeEmbeddingOptions.builder().build();
        }
        var extraBody = new LinkedHashMap<String, Object>();
        extraBody.putAll(ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "gitee-moark"));
        return GiteeEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .instructions(request.getInstructions())
            .includeSparseEmbedding(Boolean.TRUE.equals(request.getIncludeSparseEmbedding()))
            .includeModalityEmbeddings(
                Boolean.TRUE.equals(request.getIncludeModalityEmbeddings()))
            .extraBody(extraBody)
            .build();
    }
}
