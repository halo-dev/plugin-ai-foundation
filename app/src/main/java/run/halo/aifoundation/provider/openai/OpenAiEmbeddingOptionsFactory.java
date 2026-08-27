package run.halo.aifoundation.provider.openai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

final class OpenAiEmbeddingOptionsFactory {

    private static final Set<String> FIELDS = Set.of("encoding_format", "user");

    private OpenAiEmbeddingOptionsFactory() {
    }

    static OpenAiEmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var values = ProviderRequestOptions.orEmpty(request.getProviderOptions(), "openai");
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported OpenAI embedding option(s): "
                + String.join(", ", unknown));
        }
        var format = string(values.get("encoding_format"), "encoding_format");
        if (format != null && !Set.of("float", "base64").contains(format)) {
            throw new IllegalArgumentException(
                "OpenAI embedding encoding_format must be 'float' or 'base64'");
        }
        return new OpenAiEmbeddingOptions(null, null, null, request.getDimensions(), format,
            string(values.get("user"), "user"), Map.of(), request.getHeaders(), null);
    }

    private static String string(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("OpenAI embedding " + field
            + " must be a non-blank string");
    }
}
