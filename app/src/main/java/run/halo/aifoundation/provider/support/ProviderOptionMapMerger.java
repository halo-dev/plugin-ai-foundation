package run.halo.aifoundation.provider.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Merges provider option objects while preserving unrelated nested fields. */
public final class ProviderOptionMapMerger {

    private ProviderOptionMapMerger() {
    }

    public static Map<String, Object> merge(Map<String, Object> base,
        Map<String, Object> overrides) {
        var merged = copy(base);
        if (overrides == null) {
            return merged;
        }
        for (var entry : overrides.entrySet()) {
            var name = entry.getKey();
            merged.put(name, mergeValue(merged.get(name), entry.getValue()));
        }
        return merged;
    }

    private static Object mergeValue(Object base, Object override) {
        if (!(base instanceof Map<?, ?> baseMap)) {
            return copyValue(override);
        }
        if (!(override instanceof Map<?, ?> overrideMap)) {
            return copyValue(override);
        }
        return merge(stringMap(baseMap), stringMap(overrideMap));
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        var copy = new LinkedHashMap<String, Object>();
        if (source == null) {
            return copy;
        }
        for (var entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copy(stringMap(map));
        }
        return value;
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        var values = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String field)) {
                throw new IllegalArgumentException("Provider option object keys must be strings");
            }
            values.put(field, entry.getValue());
        }
        return values;
    }
}
