package run.halo.aifoundation.provider.openrouter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

/** Validates the documented request options accepted by OpenRouter's embeddings router. */
final class OpenRouterEmbeddingOptionsFactory {

    private static final Set<String> FIELDS = Set.of(
        "provider", "user", "encoding_format", "input_type");

    private OpenRouterEmbeddingOptionsFactory() {
    }

    static EmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var values = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "openrouter");
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported OpenRouter embedding option(s): "
                + String.join(", ", unknown));
        }
        if (!hasRuntimeOverrides(request, values)) {
            return null;
        }
        var format = string(values.get("encoding_format"), "encoding_format");
        if (format != null && !Set.of("float", "base64").contains(format)) {
            throw new IllegalArgumentException(
                "OpenRouter encoding_format must be 'float' or 'base64'");
        }
        OpenRouterRoutingOptions.validate(values.get("provider"), "embedding");
        return OpenRouterEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .encodingFormat(format)
            .inputType(string(values.get("input_type"), "input_type"))
            .user(string(values.get("user"), "user"))
            .provider(object(values.get("provider"), "provider"))
            .customHeaders(request.getHeaders())
            .build();
    }

    private static boolean hasRuntimeOverrides(EmbeddingRequest request,
        Map<String, Object> values) {
        if (request.getDimensions() != null) {
            return true;
        }
        if (!values.isEmpty()) {
            return true;
        }
        if (request.getHeaders() == null) {
            return false;
        }
        return !request.getHeaders().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("OpenRouter " + field + " must be an object");
    }

    private static String string(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("OpenRouter " + field + " must be a non-blank string");
    }
}
