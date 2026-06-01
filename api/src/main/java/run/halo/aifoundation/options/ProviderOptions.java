package run.halo.aifoundation.options;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Type-safe helper for building external provider-neutral provider options.
 *
 * <p>Provider options are intentionally grouped by provider namespace. The public SDK keeps this
 * helper provider-neutral; provider-specific option names should be exposed by provider metadata or
 * dedicated provider APIs instead of being hardcoded here for only a subset of providers.
 *
 * <pre>{@code
 * var options = ProviderOptions.of(
 *     ProviderOptions.namespace("openai")
 *         .option("seed", 42)
 *         .option("dimensions", 512)
 *         .build()
 * );
 * }</pre>
 */
public final class ProviderOptions {

    private ProviderOptions() {
    }

    /**
     * Creates an arbitrary provider namespace builder.
     */
    public static NamespaceBuilder namespace(String namespace) {
        return new NamespaceBuilder(namespace);
    }

    /**
     * Combines namespace options into the public providerOptions map shape.
     */
    public static Map<String, Map<String, Object>> of(NamespaceOptions... options) {
        var values = new LinkedHashMap<String, Map<String, Object>>();
        if (options == null) {
            return values;
        }
        for (var option : options) {
            if (option == null || option.namespace() == null || option.namespace().isBlank()) {
                continue;
            }
            values.put(option.namespace(), new LinkedHashMap<>(option.options()));
        }
        return values;
    }

    /**
     * Provider namespaced options.
     */
    public record NamespaceOptions(String namespace, Map<String, Object> options) {
    }

    /**
     * Builder for one provider namespace.
     */
    public static final class NamespaceBuilder {
        private final String namespace;
        private final LinkedHashMap<String, Object> options = new LinkedHashMap<>();

        private NamespaceBuilder(String namespace) {
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException("provider options namespace must not be blank");
            }
            this.namespace = namespace;
        }

        /**
         * Adds a provider-specific option. This is the explicit escape hatch for options that do
         * not have a typed helper yet.
         */
        public NamespaceBuilder option(String name, Object value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("provider option name must not be blank");
            }
            if (value != null) {
                options.put(name, value);
            }
            return this;
        }

        public NamespaceOptions build() {
            return new NamespaceOptions(namespace, Map.copyOf(options));
        }
    }
}
