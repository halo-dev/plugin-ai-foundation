package run.halo.aifoundation.provider.aihubmix;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

/** Validates AIHubMix's documented Jina embedding fields. */
final class AiHubMixEmbeddingOptionsFactory {

    private static final Set<String> FIELDS = Set.of("embedding_format", "user");

    private AiHubMixEmbeddingOptionsFactory() {
    }

    static AiHubMixEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var values = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "aihubmix");
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported AIHubMix embedding option(s): "
                + String.join(", ", unknown));
        }
        if (hasInstructions(request)) {
            throw new IllegalArgumentException(
                "AIHubMix embeddings do not document an instructions field");
        }
        if (Boolean.TRUE.equals(request.getIncludeSparseEmbedding())) {
            throw new IllegalArgumentException(
                "AIHubMix embeddings return dense vectors only");
        }
        if (Boolean.TRUE.equals(request.getIncludeModalityEmbeddings())) {
            throw new IllegalArgumentException(
                "AIHubMix embeddings return dense vectors only");
        }
        var format = string(values.get("embedding_format"), "embedding_format");
        if (format != null && !Set.of("float", "base64").contains(format)) {
            throw new IllegalArgumentException(
                "AIHubMix embedding_format must be 'float' or 'base64'");
        }
        return AiHubMixEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .embeddingFormat(format)
            .user(string(values.get("user"), "user"))
            .customHeaders(request.getHeaders())
            .build();
    }

    private static boolean hasInstructions(EmbeddingRequest request) {
        if (request.getInstructions() == null) {
            return false;
        }
        return !request.getInstructions().isBlank();
    }

    private static String string(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("AIHubMix embedding " + field
            + " must be a non-blank string");
    }
}
