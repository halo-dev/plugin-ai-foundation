package run.halo.aifoundation.provider.support;

import java.util.Map;

/** Safe access to namespaced provider options on provider-neutral requests. */
public final class ProviderRequestOptions {

    private ProviderRequestOptions() {
    }

    public static Map<String, Object> get(
        Map<String, Map<String, Object>> providerOptions, String providerType) {
        if (providerOptions == null) {
            return null;
        }
        return providerOptions.get(providerType);
    }

    public static Map<String, Object> orEmpty(
        Map<String, Map<String, Object>> providerOptions, String providerType) {
        var values = get(providerOptions, providerType);
        if (values == null) {
            return Map.of();
        }
        return values;
    }

    public static void copyNonNullValues(Map<String, Object> target,
        Map<String, Object> values) {
        if (values == null) {
            return;
        }
        values.forEach((name, value) -> putIfPresent(target, name, value));
    }

    private static void putIfPresent(Map<String, Object> target, String name, Object value) {
        if (name == null) {
            return;
        }
        if (value == null) {
            return;
        }
        target.put(name, value);
    }
}
