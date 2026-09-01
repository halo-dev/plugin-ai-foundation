package run.halo.aifoundation.provider.support;

import java.util.Map;

/** Reads the provider namespace used to replay native reasoning across tool continuations. */
public final class ReasoningProviderMetadata {

    private static final String ROOT_KEY = "reasoningProviderMetadata";

    private ReasoningProviderMetadata() {
    }

    public static Map<String, Object> values(Map<String, Object> metadata,
        String providerType) {
        if (metadata == null) {
            return Map.of();
        }
        if (providerType == null) {
            return Map.of();
        }
        var namespaces = map(metadata.get(ROOT_KEY));
        if (namespaces == null) {
            return Map.of();
        }
        var values = map(namespaces.get(providerType));
        return values != null ? values : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
