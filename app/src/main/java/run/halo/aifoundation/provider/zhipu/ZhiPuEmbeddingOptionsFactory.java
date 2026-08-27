package run.halo.aifoundation.provider.zhipu;

import java.util.ArrayList;
import java.util.List;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

/** Rejects options that the current BigModel text embedding contract does not define. */
final class ZhiPuEmbeddingOptionsFactory {

    private ZhiPuEmbeddingOptionsFactory() {
    }

    static ZhiPuEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var nativeOptions = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "zhipuai");
        if (!nativeOptions.isEmpty()) {
            throw new IllegalArgumentException(
                "Zhipu embedding does not document provider-specific request fields: "
                    + String.join(", ", new ArrayList<>(nativeOptions.keySet())));
        }
        if (hasInstructions(request)) {
            throw new IllegalArgumentException(
                "Zhipu text embeddings do not document an instructions field");
        }
        if (hasNonDenseOutput(request)) {
            throw new IllegalArgumentException(
                "Zhipu text embeddings do not return sparse or modality embeddings");
        }
        return ZhiPuEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .customHeaders(request.getHeaders())
            .build();
    }

    private static boolean hasInstructions(EmbeddingRequest request) {
        if (request.getInstructions() == null) {
            return false;
        }
        return !request.getInstructions().isBlank();
    }

    private static boolean hasNonDenseOutput(EmbeddingRequest request) {
        if (Boolean.TRUE.equals(request.getIncludeSparseEmbedding())) {
            return true;
        }
        return Boolean.TRUE.equals(request.getIncludeModalityEmbeddings());
    }
}
