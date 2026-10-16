package run.halo.aifoundation.tool.builtin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common strict JSON Schema shapes used by Halo tool domains.
 *
 * <p>Business fields remain next to their executor. For example, a draft schema adds its content fields to
 * {@link #object(Map, List)} rather than sharing a central tool catalog.
 */
final class HaloToolSchemas {
    private HaloToolSchemas() {}

    static Map<String, Object> paginationProperties() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10_000, "default", 1));
        properties.put("size", Map.of("type", "integer", "minimum", 1, "maximum", 100, "default", 20));
        properties.put(
                "query",
                string(200, "Optional keyword; searched fields depend on the list tool, not a full-text body search"));
        return properties;
    }

    static Map<String, Object> pagination() {
        return object(paginationProperties(), List.of());
    }

    /**
     * Describes the query and result-count inputs shared by site and internet search.
     *
     * <p>For example, {@code {"query":"Halo","limit":5}} requests at most five results. A query is required; executors
     * use five results when {@code limit} is omitted.
     *
     * @return a strict object schema accepting a non-empty query and one to ten results
     */
    static Map<String, Object> search() {
        return object(
                Map.of(
                        "query",
                                Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "Search query",
                                        "minLength",
                                        1,
                                        "maxLength",
                                        500),
                        "limit",
                                Map.of(
                                        "type",
                                        "integer",
                                        "description",
                                        "Maximum results, default 5",
                                        "minimum",
                                        1,
                                        "maximum",
                                        10)),
                List.of("query"));
    }

    static Map<String, Object> string(int limit, String description) {
        return Map.of("type", "string", "maxLength", limit, "description", description);
    }

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }
}
