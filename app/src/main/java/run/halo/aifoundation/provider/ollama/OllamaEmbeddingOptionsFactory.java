package run.halo.aifoundation.provider.ollama;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;

/** Maps provider-neutral embedding requests to Ollama's native embed options. */
final class OllamaEmbeddingOptionsFactory {

    private static final Set<String> PROVIDER_FIELDS = Set.of("truncate", "options");
    private static final Set<String> RUNTIME_FIELDS = Set.of(
        "numa", "num_batch", "num_gpu", "main_gpu", "low_vram", "vocab_only",
        "use_mmap", "use_mlock", "num_thread");

    private OllamaEmbeddingOptionsFactory() {
    }

    static EmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions providerOptions, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var source = ProviderRequestOptions.orEmpty(request.getProviderOptions(), "ollama");
        var unknown = new LinkedHashSet<>(source.keySet());
        unknown.removeAll(PROVIDER_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Ollama embedding provider option(s): "
                + String.join(", ", unknown));
        }
        var runtime = source.get("options") instanceof Map<?, ?> map ? map : Map.of();
        if (source.get("options") != null && !(source.get("options") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                "Ollama embedding provider option 'options' must be an object");
        }
        var unknownRuntime = new LinkedHashSet<String>();
        runtime.keySet().forEach(key -> {
            if (key != null && !RUNTIME_FIELDS.contains(key.toString())) {
                unknownRuntime.add(key.toString());
            }
        });
        if (!unknownRuntime.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Ollama embedding runtime option(s): "
                + String.join(", ", unknownRuntime));
        }
        if (request.getDimensions() == null && source.isEmpty()) {
            return null;
        }
        var normalizedRuntime = new java.util.LinkedHashMap<String, Object>();
        runtime.forEach((key, value) -> {
            if (key != null && value != null) {
                normalizedRuntime.put(key.toString(), value);
            }
        });
        return new OllamaEmbeddingOptions(null, request.getDimensions(),
            bool(source.get("truncate"), "truncate"), Map.copyOf(normalizedRuntime));
    }

    private static Boolean bool(Object value, String field) {
        if (value == null || value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalArgumentException("Ollama " + field + " must be a boolean");
    }

}
