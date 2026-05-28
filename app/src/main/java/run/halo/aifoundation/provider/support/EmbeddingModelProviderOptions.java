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
            warnUnsupportedProviderOptions(request, warnings);
            return defaultOptions(request);
        }
        return embeddingOptionsFactory.build(request, this, warnings);
    }

    public Map<String, Object> namespacedOptions(EmbeddingRequest request) {
        if (request == null || request.getProviderOptions() == null
            || providerOptionsNamespace == null) {
            return Map.of();
        }
        var options = request.getProviderOptions().get(providerOptionsNamespace);
        return options != null ? options : Map.of();
    }

    private void warnUnsupportedProviderOptions(EmbeddingRequest request,
        List<EmbeddingWarning> warnings) {
        if (request == null || request.getProviderOptions() == null
            || request.getProviderOptions().isEmpty()) {
            return;
        }
        for (var namespace : request.getProviderOptions().keySet()) {
            warnings.add(EmbeddingWarning.builder()
                .code("unsupported-provider-option")
                .message("Embedding provider options are not supported for namespace: "
                    + namespace)
                .providerMetadata(Map.of("namespace", namespace))
                .build());
        }
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
