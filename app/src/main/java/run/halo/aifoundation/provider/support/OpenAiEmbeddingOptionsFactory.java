package run.halo.aifoundation.provider.support;

import java.util.List;
import java.util.Map;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import run.halo.aifoundation.EmbeddingRequest;
import run.halo.aifoundation.EmbeddingWarning;

public final class OpenAiEmbeddingOptionsFactory {

    private OpenAiEmbeddingOptionsFactory() {
    }

    public static org.springframework.ai.embedding.EmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions providerOptions, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var namespaceOptions = providerOptions.namespacedOptions(request);
        warnOtherNamespaces(request, providerOptions.providerOptionsNamespace(), warnings);

        var dimensions = intOption(namespaceOptions, "dimensions", request.getDimensions());
        var user = stringOption(namespaceOptions, "user");
        var encodingFormat = stringOption(namespaceOptions, "encodingFormat");
        warnUnsupportedOptions(namespaceOptions, warnings);

        if (dimensions == null && user == null && encodingFormat == null) {
            return null;
        }
        return OpenAiEmbeddingOptions.builder()
            .dimensions(dimensions)
            .user(user)
            .encodingFormat(encodingFormat)
            .build();
    }

    private static Integer intOption(Map<String, Object> options, String key, Integer fallback) {
        var value = options.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static String stringOption(Map<String, Object> options, String key) {
        var value = options.get(key);
        return value != null ? value.toString() : null;
    }

    private static void warnOtherNamespaces(EmbeddingRequest request, String supportedNamespace,
        List<EmbeddingWarning> warnings) {
        if (request.getProviderOptions() == null) {
            return;
        }
        for (var namespace : request.getProviderOptions().keySet()) {
            if (!namespace.equals(supportedNamespace)) {
                warnings.add(EmbeddingWarning.builder()
                    .code("ignored-provider-option-namespace")
                    .message("Embedding provider options namespace is ignored: " + namespace)
                    .providerMetadata(Map.of("namespace", namespace))
                    .build());
            }
        }
    }

    private static void warnUnsupportedOptions(Map<String, Object> options,
        List<EmbeddingWarning> warnings) {
        for (var key : options.keySet()) {
            if (!List.of("dimensions", "user", "encodingFormat").contains(key)) {
                warnings.add(EmbeddingWarning.builder()
                    .code("unsupported-provider-option")
                    .message("OpenAI-compatible embedding option is not supported: " + key)
                    .providerMetadata(Map.of("namespace", "openai", "option", key))
                    .build());
            }
        }
    }
}
