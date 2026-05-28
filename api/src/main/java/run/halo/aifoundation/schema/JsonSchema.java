package run.halo.aifoundation.schema;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe helper for creating the provider-neutral JSON Schema maps used by tools and
 * structured output.
 *
 * <p>The SDK still serializes schemas as Java collection types internally, but callers should
 * prefer this class over handwritten maps so valid schema keywords are discoverable in an IDE.
 *
 * <pre>{@code
 * var schema = JsonSchema.object()
 *     .property("location", JsonSchema.string().description("City name"))
 *     .required("location")
 *     .build();
 * }</pre>
 */
public final class JsonSchema {

    private final Map<String, Object> values;

    private JsonSchema(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    /**
     * Creates an object schema builder.
     */
    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    /**
     * Creates a string schema builder.
     */
    public static ScalarBuilder string() {
        return scalar("string");
    }

    /**
     * Creates an integer schema builder.
     */
    public static ScalarBuilder integer() {
        return scalar("integer");
    }

    /**
     * Creates a number schema builder.
     */
    public static ScalarBuilder number() {
        return scalar("number");
    }

    /**
     * Creates a boolean schema builder.
     */
    public static ScalarBuilder bool() {
        return scalar("boolean");
    }

    /**
     * Creates an array schema builder.
     */
    public static ArrayBuilder array(JsonSchema items) {
        return new ArrayBuilder(items);
    }

    /**
     * Creates a string enum schema builder.
     */
    public static ScalarBuilder enumeration(String... values) {
        return enumeration(Arrays.asList(values));
    }

    /**
     * Creates a string enum schema builder.
     */
    public static ScalarBuilder enumeration(Collection<String> values) {
        return string().keyword("enum", List.copyOf(values));
    }

    /**
     * Derives a schema from a Java record or simple POJO.
     */
    public static JsonSchema fromClass(Class<?> type) {
        return fromMap(StructuredSchema.fromClass(type));
    }

    /**
     * Wraps an existing provider-neutral schema map.
     */
    public static JsonSchema fromMap(Map<String, Object> schema) {
        return new JsonSchema(copy(schema));
    }

    /**
     * Returns a detached provider-neutral map representation.
     */
    public Map<String, Object> toMap() {
        return copy(values);
    }

    private static ScalarBuilder scalar(String type) {
        return new ScalarBuilder(type);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    /**
     * Base builder for schema types.
     */
    public abstract static class Builder<T extends Builder<T>> {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        Builder(String type) {
            values.put("type", type);
        }

        /**
         * Adds a human-readable schema description.
         */
        public T description(String description) {
            return keyword("description", description);
        }

        /**
         * Adds a custom JSON Schema keyword for advanced provider-neutral metadata.
         */
        public T keyword(String name, Object value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("schema keyword name must not be blank");
            }
            if (value != null) {
                values.put(name, value);
            }
            return self();
        }

        /**
         * Builds an immutable schema helper.
         */
        public JsonSchema build() {
            return new JsonSchema(values);
        }

        /**
         * Builds a provider-neutral map.
         */
        public Map<String, Object> toMap() {
            return build().toMap();
        }

        abstract T self();
    }

    /**
     * Builder for scalar schemas.
     */
    public static final class ScalarBuilder extends Builder<ScalarBuilder> {
        private ScalarBuilder(String type) {
            super(type);
        }

        @Override
        ScalarBuilder self() {
            return this;
        }
    }

    /**
     * Builder for object schemas.
     */
    public static final class ObjectBuilder extends Builder<ObjectBuilder> {
        private final LinkedHashMap<String, Object> properties = new LinkedHashMap<>();

        private ObjectBuilder() {
            super("object");
        }

        /**
         * Adds a named object property.
         */
        public ObjectBuilder property(String name, JsonSchema schema) {
            return property(name, schema != null ? schema.toMap() : Map.of());
        }

        /**
         * Adds a named object property from a schema builder.
         */
        public ObjectBuilder property(String name, Builder<?> schema) {
            return property(name, schema != null ? schema.build() : null);
        }

        /**
         * Adds a named object property from a raw schema map.
         */
        public ObjectBuilder property(String name, Map<String, Object> schema) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("schema property name must not be blank");
            }
            properties.put(name, copy(schema));
            return this;
        }

        /**
         * Marks object properties as required.
         */
        public ObjectBuilder required(String... names) {
            return required(Arrays.asList(names));
        }

        /**
         * Marks object properties as required.
         */
        public ObjectBuilder required(Collection<String> names) {
            if (names != null && !names.isEmpty()) {
                keyword("required", names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .toList());
            }
            return this;
        }

        @Override
        public JsonSchema build() {
            if (!properties.isEmpty()) {
                keyword("properties", new LinkedHashMap<>(properties));
            }
            return super.build();
        }

        @Override
        ObjectBuilder self() {
            return this;
        }
    }

    /**
     * Builder for array schemas.
     */
    public static final class ArrayBuilder extends Builder<ArrayBuilder> {
        private ArrayBuilder(JsonSchema items) {
            super("array");
            keyword("items", items != null ? items.toMap() : Map.of());
        }

        @Override
        ArrayBuilder self() {
            return this;
        }
    }
}
