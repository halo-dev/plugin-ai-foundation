package run.halo.aifoundation.tool;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serializable catalog metadata. Executors, approval policies, and lifecycle callbacks are never
 * exposed by discovery. {@code id} is the stable selection value; {@code name} is for the model.
 */
public record ToolDescriptor(
    String id,
    String pluginName,
    String localName,
    String name,
    String displayName,
    String description,
    String displayDescription,
    String category,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    List<String> tags,
    List<Map<String, Object>> configurationSchema,
    boolean enabled,
    boolean configured,
    String status
) {
    public ToolDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(pluginName, "pluginName must not be null");
        Objects.requireNonNull(localName, "localName must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(inputSchema, "inputSchema must not be null");
        inputSchema = copyObject(inputSchema, 0);
        outputSchema = outputSchema != null ? copyObject(outputSchema, 0) : null;
        tags = tags == null ? List.of() : List.copyOf(tags);
        configurationSchema = configurationSchema == null ? List.of()
            : configurationSchema.stream().map(schema -> copyObject(schema, 0)).toList();
        status = !enabled ? "DISABLED" : configured ? "ENABLED" : "UNCONFIGURED";
    }

    /** Retains the original catalog constructor for Java consumers. */
    public ToolDescriptor(String id, String pluginName, String localName, String name,
        String displayName, String description, String displayDescription, String category,
        Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
        this(id, pluginName, localName, name, displayName, description, displayDescription, category,
            inputSchema, outputSchema, List.of(), List.of(), true, true, "ENABLED");
    }

    private static Map<String, Object> copyObject(Map<?, ?> value, int depth) {
        var copy = new LinkedHashMap<String, Object>();
        value.forEach((key, child) -> {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            copy.put(text, copyJson(child, depth + 1));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyJson(Object value, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Tool schema exceeds the maximum nesting depth");
        }
        if (value instanceof Map<?, ?> map) {
            return copyObject(map, depth);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<>();
            list.forEach(child -> copy.add(copyJson(child, depth + 1)));
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Boolean
            || value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal
            || value instanceof Double doubleValue && Double.isFinite(doubleValue)
            || value instanceof Float floatValue && Float.isFinite(floatValue)) {
            return value;
        }
        throw new IllegalArgumentException("Tool schemas must contain only JSON values");
    }
}
