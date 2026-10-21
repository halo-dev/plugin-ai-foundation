package run.halo.aifoundation.provider.tokenhub;

import java.util.ArrayList;
import java.util.List;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

/** Rejects request fields the TokenHub text embedding contract does not define. */
final class TokenHubEmbeddingOptionsFactory {

    private TokenHubEmbeddingOptionsFactory() {
    }

    static TokenHubEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions providerOptions, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var nativeOptions = providerOptions.nativeOptions();
        if (!nativeOptions.isEmpty()) {
            throw new IllegalArgumentException(
                "TokenHub embeddings do not document provider-specific request fields: "
                    + String.join(", ", new ArrayList<>(nativeOptions.keySet())));
        }
        if (request.getDimensions() != null) {
            throw new IllegalArgumentException(
                "TokenHub embedding dimensions are fixed by the selected model");
        }
        if (request.getInstructions() != null && !request.getInstructions().isBlank()) {
            throw new IllegalArgumentException(
                "TokenHub text embeddings do not document an instructions field");
        }
        if (Boolean.TRUE.equals(request.getIncludeSparseEmbedding())
            || Boolean.TRUE.equals(request.getIncludeModalityEmbeddings())) {
            throw new IllegalArgumentException(
                "TokenHub text embeddings return dense vectors only");
        }
        return TokenHubEmbeddingOptions.builder()
            .customHeaders(request.getHeaders())
            .build();
    }
}
