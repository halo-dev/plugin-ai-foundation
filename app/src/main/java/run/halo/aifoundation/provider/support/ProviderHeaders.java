package run.halo.aifoundation.provider.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared rules for merging caller-provided provider headers. */
public final class ProviderHeaders {

    private ProviderHeaders() {
    }

    public static Map<String, String> merge(Map<String, String> defaults,
        Map<String, String> providerOverrides, Map<String, String> requestHeaders) {
        var merged = new LinkedHashMap<String, String>();
        putAll(merged, defaults);
        putAll(merged, providerOverrides);
        putValidHeaders(merged, requestHeaders);
        return Map.copyOf(merged);
    }

    public static void putIfValid(Map<String, String> target, String name, String value) {
        if (name == null) {
            return;
        }
        if (name.isBlank()) {
            return;
        }
        if (value == null) {
            return;
        }
        target.put(name, value);
    }

    private static void putAll(Map<String, String> target, Map<String, String> source) {
        if (source == null) {
            return;
        }
        target.putAll(source);
    }

    private static void putValidHeaders(Map<String, String> target,
        Map<String, String> source) {
        if (source == null) {
            return;
        }
        source.forEach((name, value) -> putIfValid(target, name, value));
    }
}
