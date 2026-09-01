package run.halo.aifoundation.provider.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Creates immutable provider metadata maps without retaining absent JSON fields. */
public final class ProviderMetadataMaps {

    private ProviderMetadataMaps() {
    }

    public static Map<String, Object> immutableNonNull(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        if (metadata.isEmpty()) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, Object>();
        metadata.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (value == null) {
                return;
            }
            values.put(key, value);
        });
        if (values.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(values);
    }

    public static Map<String, Object> namespaced(String namespace,
        Map<String, Object> metadata) {
        var values = immutableNonNull(metadata);
        if (values.isEmpty()) {
            return Map.of();
        }
        return Map.of(namespace, values);
    }
}
