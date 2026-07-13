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
    String providerOptionsNamespace,
    EmbeddingOptionsFactory embeddingOptionsFactory
) {
    public static EmbeddingModelProviderOptions defaults(String providerType) {
        return new EmbeddingModelProviderOptions(providerType, null);
    }

    public EmbeddingOptions buildOptions(EmbeddingRequest request,
        List<EmbeddingWarning> warnings) {
        if (embeddingOptionsFactory == null) {
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
