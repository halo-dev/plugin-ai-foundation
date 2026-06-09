package run.halo.aifoundation.provider.support.openai;

import java.util.List;
import java.util.Map;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

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
        var encodingFormat = encodingFormatOption(stringOption(namespaceOptions, "encodingFormat"));
        warnUnsupportedOptions(namespaceOptions, warnings);

        var headers = request.getHeaders() != null ? request.getHeaders() : Map.<String, String>of();
        if (dimensions == null && user == null && encodingFormat == null && headers.isEmpty()) {
            return null;
        }
        var builder = OpenAiCompatibleEmbeddingOptions.builder()
            .dimensions(dimensions)
            .user(user)
            .encodingFormat(encodingFormat);
        builder.customHeaders(headers);
        return builder.build();
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

    private static OpenAiCompatibleEmbeddingOptions.EncodingFormat encodingFormatOption(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "float" -> OpenAiCompatibleEmbeddingOptions.EncodingFormat.FLOAT;
            case "base64" -> OpenAiCompatibleEmbeddingOptions.EncodingFormat.BASE64;
            default -> throw new IllegalArgumentException(
                "Unsupported OpenAI-compatible embedding encodingFormat: " + value);
        };
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
