package run.halo.aifoundation.provider.support;

import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Resolves request-scoped embedding options without hiding provider-specific fields.
 *
 * <p>Standard {@link EmbeddingOptions} values can override every provider's defaults. Values
 * unique to a provider are read only when the request uses that provider's options type.</p>
 */
public final class EmbeddingOptionOverrides<T extends EmbeddingOptions> {

    private final EmbeddingOptions request;
    private final T providerRequest;

    private EmbeddingOptionOverrides(EmbeddingOptions request, Class<T> providerType) {
        this.request = request;
        this.providerRequest = providerRequest(request, providerType);
    }

    public static <T extends EmbeddingOptions> EmbeddingOptionOverrides<T> from(
        EmbeddingOptions request, Class<T> providerType) {
        return new EmbeddingOptionOverrides<>(request, providerType);
    }

    public String modelOr(String fallback) {
        if (request == null) {
            return fallback;
        }
        return textOrFallback(request.getModel(), fallback);
    }

    public Integer dimensionsOr(Integer fallback) {
        if (request == null) {
            return fallback;
        }
        return valueOrFallback(request.getDimensions(), fallback);
    }

    public String textOr(Function<T, String> extractor, String fallback) {
        if (providerRequest == null) {
            return fallback;
        }
        return textOrFallback(extractor.apply(providerRequest), fallback);
    }

    public <V> V valueOr(Function<T, V> extractor, V fallback) {
        if (providerRequest == null) {
            return fallback;
        }
        return valueOrFallback(extractor.apply(providerRequest), fallback);
    }

    public boolean booleanOr(Predicate<T> extractor, boolean fallback) {
        if (providerRequest == null) {
            return fallback;
        }
        return extractor.test(providerRequest);
    }

    public <V> V providerValue(Function<T, V> extractor) {
        if (providerRequest == null) {
            return null;
        }
        return extractor.apply(providerRequest);
    }

    private static String textOrFallback(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static <V> V valueOrFallback(V value, V fallback) {
        if (value == null) {
            return fallback;
        }
        return value;
    }

    private static <T extends EmbeddingOptions> T providerRequest(EmbeddingOptions request,
        Class<T> providerType) {
        if (!providerType.isInstance(request)) {
            return null;
        }
        return providerType.cast(request);
    }
}
