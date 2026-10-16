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
 * Serializable catalog metadata. Executors, approval policies, and lifecycle callbacks are never exposed by discovery.
 * {@code id} is the stable selection value; {@code name} is for the model.
 *
 * <p>For example, a selector persists {@code article-tools/list_posts} from {@link #id()}, then passes it to
 * {@link run.halo.aifoundation.AiToolService#resolveTools(List)}. It uses display fields for presentation and
 * {@link #enabled()}, {@link #configured()}, and {@link #status()} to explain why a catalog entry cannot currently be
 * selected. Schemas and lists are immutable snapshots.
 *
 * @param id stable {@code pluginId/localName} selection identity
 * @param pluginName owning plugin identity
 * @param localName provider-local definition name
 * @param name unique model-facing name assigned by the registry
 * @param displayName administrator-facing title
 * @param description model-facing description
 * @param displayDescription administrator-facing explanation
 * @param category shared catalog category
 * @param inputSchema JSON Schema for model-generated arguments
 * @param outputSchema optional JSON Schema for execution results
 * @param tags searchable presentation labels
 * @param configurationSchema optional FormKit nodes for the settings dialog
 * @param enabled whether an administrator permits use of this tool
 * @param configured whether required provider settings are ready
 * @param status derived state; constructor input is replaced from enabled and configured flags
 * @param group optional group metadata
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
        String status,
        ToolGroupDescriptor group) {
    /**
     * Validates catalog identities, snapshots JSON metadata, and derives the displayed status.
     *
     * @throws IllegalArgumentException if schemas contain unsupported JSON values or nesting
     * @throws NullPointerException if an identity or the input schema is {@code null}
     */
    public ToolDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(pluginName, "pluginName must not be null");
        Objects.requireNonNull(localName, "localName must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(inputSchema, "inputSchema must not be null");
        inputSchema = copyObject(inputSchema, 0);
        outputSchema = outputSchema != null ? copyObject(outputSchema, 0) : null;
        tags = tags == null ? List.of() : List.copyOf(tags);
        configurationSchema = configurationSchema == null
                ? List.of()
                : configurationSchema.stream()
                        .map(schema -> copyObject(schema, 0))
                        .toList();
        status = !enabled ? "DISABLED" : configured ? "ENABLED" : "UNCONFIGURED";
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
        if (isJsonScalar(value)) {
            return value;
        }
        throw new IllegalArgumentException("Tool schemas must contain only JSON values");
    }

    private static boolean isJsonScalar(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return true;
        }
        if (value instanceof Double number) {
            return Double.isFinite(number);
        }
        if (value instanceof Float number) {
            return Float.isFinite(number);
        }
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }
}
