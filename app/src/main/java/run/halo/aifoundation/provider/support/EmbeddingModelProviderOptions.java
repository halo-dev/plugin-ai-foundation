package run.halo.aifoundation.provider.support;

import java.util.List;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;

/**
 * Provider-specific switches used by the generic embedding model implementation.
 */
public record EmbeddingModelProviderOptions(
    EmbeddingOptionsFactory embeddingOptionsFactory,
    Map<String, Object> nativeOptions
) {
    public EmbeddingModelProviderOptions {
        nativeOptions = nativeOptions == null ? Map.of() : Map.copyOf(nativeOptions);
    }

    public EmbeddingModelProviderOptions(EmbeddingOptionsFactory embeddingOptionsFactory) {
        this(embeddingOptionsFactory, Map.of());
    }

    public static EmbeddingModelProviderOptions defaults() {
        return new EmbeddingModelProviderOptions(null, Map.of());
    }

    public EmbeddingModelProviderOptions withNativeOptions(Map<String, Object> options) {
        return new EmbeddingModelProviderOptions(embeddingOptionsFactory, options);
    }

    public EmbeddingOptions buildOptions(EmbeddingRequest request,
        List<EmbeddingWarning> warnings) {
        if (embeddingOptionsFactory == null) {
            if (!nativeOptions.isEmpty()) {
                throw new IllegalStateException(
                    "Embedding provider does not support configured native options");
            }
            return defaultOptions(request);
        }
        return embeddingOptionsFactory.build(request, this, warnings);
    }

    private EmbeddingOptions defaultOptions(EmbeddingRequest request) {
        if (request == null || request.getDimensions() == null) {
            return null;
        }
        return new EmbeddingOptions() {
            @Override
            public String getModel() {
                return null;
            }

            @Override
            public Integer getDimensions() {
                return request.getDimensions();
            }
        };
    }
}
