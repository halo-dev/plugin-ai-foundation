package run.halo.aifoundation.provider.openrouter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared validation for OpenRouter's provider-routing object across API domains. */
final class OpenRouterRoutingOptions {

    private static final Set<String> FIELDS = Set.of(
        "order", "allow_fallbacks", "require_parameters", "data_collection", "zdr",
        "only", "ignore", "quantizations", "sort", "max_price", "options");

    private OpenRouterRoutingOptions() {
    }

    static void validate(Object value, String domain) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> routing)) {
            throw new IllegalArgumentException("OpenRouter " + domain
                + " provider routing must be an object");
        }
        var unknown = new java.util.LinkedHashSet<String>();
        routing.keySet().forEach(key -> {
            if (key == null || !FIELDS.contains(key.toString())) {
                unknown.add(String.valueOf(key));
            }
        });
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported OpenRouter " + domain
                + " provider routing option(s): " + String.join(", ", unknown));
        }
        validateEnum(routing, "data_collection", Set.of("allow", "deny"), domain);
        validateEnum(routing, "sort", Set.of("price", "throughput", "latency"), domain);
        for (var field : List.of("allow_fallbacks", "require_parameters", "zdr")) {
            if (routing.get(field) != null && !(routing.get(field) instanceof Boolean)) {
                throw new IllegalArgumentException("OpenRouter " + domain + " provider." + field
                    + " must be a boolean");
            }
        }
        for (var field : List.of("order", "only", "ignore", "quantizations")) {
            if (routing.get(field) != null && !(routing.get(field) instanceof List<?>)) {
                throw new IllegalArgumentException("OpenRouter " + domain + " provider." + field
                    + " must be an array");
            }
        }
        for (var field : List.of("max_price", "options")) {
            if (routing.get(field) != null && !(routing.get(field) instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("OpenRouter " + domain + " provider." + field
                    + " must be an object");
            }
        }
    }

    private static void validateEnum(Map<?, ?> map, String field, Set<String> allowed,
        String domain) {
        var value = map.get(field);
        if (value != null && !allowed.contains(value.toString())) {
            throw new IllegalArgumentException("Unsupported OpenRouter " + domain + " provider."
                + field + ": " + value);
        }
    }
}
