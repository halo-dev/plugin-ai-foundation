package run.halo.aifoundation.provider.siliconflow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

/** Validates the documented SiliconFlow embedding options. */
final class SiliconFlowEmbeddingOptionsFactory {

    private static final Set<String> FIELDS = Set.of("encoding_format", "user", "truncate");

    private SiliconFlowEmbeddingOptionsFactory() {
    }

    static SiliconFlowEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var values = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "siliconflow");
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported SiliconFlow embedding option(s): "
                + String.join(", ", unknown));
        }
        var format = string(values.get("encoding_format"), "encoding_format");
        if (format != null && !Set.of("float", "base64").contains(format)) {
            throw new IllegalArgumentException(
                "SiliconFlow encoding_format must be 'float' or 'base64'");
        }
        var truncate = string(values.get("truncate"), "truncate");
        if (truncate != null && !Set.of("left", "right").contains(truncate)) {
            throw new IllegalArgumentException(
                "SiliconFlow truncate must be 'left' or 'right'");
        }
        return SiliconFlowEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .encodingFormat(format)
            .user(string(values.get("user"), "user"))
            .truncate(truncate)
            .instructions(request.getInstructions())
            .includeSparseEmbedding(Boolean.TRUE.equals(request.getIncludeSparseEmbedding()))
            .includeModalityEmbeddings(
                Boolean.TRUE.equals(request.getIncludeModalityEmbeddings()))
            .customHeaders(request.getHeaders())
            .build();
    }

    private static String string(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException(
            "SiliconFlow " + field + " must be a non-blank string");
    }
}
