package run.halo.aifoundation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight JSON Schema generation for Java records and simple POJOs.
 */
public final class StructuredSchema {

    private StructuredSchema() {
    }

    public static Map<String, Object> fromClass(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return schemaForType(type);
    }

    private static Map<String, Object> schemaForType(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz == String.class || clazz.isEnum()) {
                var schema = new LinkedHashMap<String, Object>();
                schema.put("type", "string");
                if (clazz.isEnum()) {
                    var values = new ArrayList<String>();
                    for (var constant : clazz.getEnumConstants()) {
                        values.add(((Enum<?>) constant).name());
                    }
                    schema.put("enum", values);
                }
                return schema;
            }
            if (clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class) {
                return Map.of("type", "integer");
            }
            if (clazz == Double.class || clazz == double.class
                || clazz == Float.class || clazz == float.class
                || Number.class.isAssignableFrom(clazz)) {
                return Map.of("type", "number");
            }
            if (clazz == Boolean.class || clazz == boolean.class) {
                return Map.of("type", "boolean");
            }
            if (List.class.isAssignableFrom(clazz)) {
                return Map.of("type", "array", "items", Map.of());
            }
            if (Map.class.isAssignableFrom(clazz)) {
                return Map.of("type", "object");
            }
            return objectSchema(clazz);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            var raw = parameterizedType.getRawType();
            if (raw instanceof Class<?> rawClass && List.class.isAssignableFrom(rawClass)) {
                var args = parameterizedType.getActualTypeArguments();
                return Map.of("type", "array", "items", args.length > 0 ? schemaForType(args[0])
                    : Map.of());
            }
        }
        return Map.of();
    }

    private static Map<String, Object> objectSchema(Class<?> type) {
        var properties = new LinkedHashMap<String, Object>();
        var required = new ArrayList<String>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                properties.put(component.getName(), schemaForType(component.getGenericType()));
                required.add(component.getName());
            }
        } else {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                properties.put(field.getName(), schemaForType(field.getGenericType()));
                if (field.getType().isPrimitive()) {
                    required.add(field.getName());
                }
            }
        }
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
