package run.halo.aifoundation.provider.support;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Protects invocation-owned request fields from administrator-defined native options. */
public final class NativeModelOptionsValidator {

    private static final Set<String> INVOCATION_FIELDS = Set.of(
        "model", "messages", "input", "prompt", "image", "images", "mask", "tools",
        "tool_choice", "documents", "query", "stream", "stream_options");
    private static final Set<String> CREDENTIAL_FIELDS = Set.of(
        "api_key", "apikey", "authorization", "access_token", "token", "secret",
        "password", "credential");
    private static final int MAX_NESTING_DEPTH = 32;

    private NativeModelOptionsValidator() {
    }

    public static void validate(Map<String, Object> options) {
        if (options == null) {
            return;
        }
        if (options.isEmpty()) {
            return;
        }
        for (var entry : options.entrySet()) {
            validateEntry(entry);
        }
    }

    private static void validateEntry(Map.Entry<String, Object> entry) {
        var name = entry.getKey();
        validateName(name, name);
        if (INVOCATION_FIELDS.contains(name)) {
            throw new IllegalArgumentException(
                "Provider-native option '" + name + "' is owned by the model invocation");
        }
        validateValue(entry.getValue(), name, 0);
    }

    private static void validateName(String name, String path) {
        if (name == null) {
            throw new IllegalArgumentException("Provider-native option names must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Provider-native option names must not be blank");
        }
        var normalizedName = name.toLowerCase(Locale.ROOT).replace('-', '_');
        if (CREDENTIAL_FIELDS.contains(normalizedName)) {
            throw new IllegalArgumentException(
                "Provider-native option '" + path + "' must use the provider Secret instead");
        }
    }

    private static void validateValue(Object value, String path, int depth) {
        if (value == null) {
            throw new IllegalArgumentException(
                "Provider-native option '" + path + "' must not be null");
        }
        if (depth >= MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException(
                "Provider-native option '" + path + "' is nested too deeply");
        }
        if (value instanceof Map<?, ?> values) {
            validateMap(values, path, depth + 1);
            return;
        }
        if (value instanceof Iterable<?> values) {
            validateValues(values, path, depth + 1);
        }
    }

    private static void validateMap(Map<?, ?> values, String parentPath, int depth) {
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new IllegalArgumentException(
                    "Provider-native option object keys must be strings: " + parentPath);
            }
            var path = parentPath + "." + name;
            validateName(name, path);
            validateValue(entry.getValue(), path, depth);
        }
    }

    private static void validateValues(Iterable<?> values, String parentPath, int depth) {
        var index = 0;
        for (var value : values) {
            validateValue(value, parentPath + "[" + index + "]", depth);
            index++;
        }
    }
}
