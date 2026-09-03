package run.halo.aifoundation.provider.doubao;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

final class DouBaoEmbeddingOptionsFactory {

    private static final Set<String> FIELDS = Set.of("encoding_format");

    private DouBaoEmbeddingOptionsFactory() {
    }

    static DouBaoEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions providerOptions, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return DouBaoEmbeddingOptions.builder().build();
        }
        var values = providerOptions.nativeOptions();
        rejectUnknownOptions(values.keySet());
        var encodingFormat = string(values.get("encoding_format"));
        if (encodingFormat != null && !Set.of("float", "base64").contains(encodingFormat)) {
            throw new IllegalArgumentException(
                "Doubao embedding encoding_format must be 'float' or 'base64'");
        }
        return DouBaoEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .encodingFormat(encodingFormat)
            .instructions(request.getInstructions())
            .includeSparseEmbedding(Boolean.TRUE.equals(request.getIncludeSparseEmbedding()))
            .includeModalityEmbeddings(
                Boolean.TRUE.equals(request.getIncludeModalityEmbeddings()))
            .build();
    }

    private static void rejectUnknownOptions(Set<String> fields) {
        var unknown = new LinkedHashSet<>(fields);
        unknown.removeAll(FIELDS);
        if (unknown.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException("Unsupported Doubao embedding option(s): "
            + String.join(", ", unknown));
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException(
            "Doubao embedding encoding_format must be a non-blank string");
    }
}
