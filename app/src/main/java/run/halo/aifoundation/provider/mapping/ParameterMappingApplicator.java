package run.halo.aifoundation.provider.mapping;

import java.util.function.Function;
/** Applies a value through a code-owned template with an optional constrained field override. */
@FunctionalInterface
public interface ParameterMappingApplicator {

    void apply(Object value, String fieldOverride, ParameterMappingTarget target);

    default void apply(Object value, ParameterMappingTarget target) {
        apply(value, null, target);
    }

    static ParameterMappingApplicator root(String field) {
        return (value, fieldOverride, target) ->
            putPath(target.root(), resolvedField(field, fieldOverride), value);
    }

    static ParameterMappingApplicator root(String field, Function<Object, Object> mapper) {
        return (value, fieldOverride, target) -> putPath(target.root(),
            resolvedField(field, fieldOverride),
            value == null ? null : mapper.apply(value));
    }

    static ParameterMappingApplicator parameters(String field) {
        return (value, fieldOverride, target) ->
            putPath(target.parameters(), resolvedField(field, fieldOverride), value);
    }

    static ParameterMappingApplicator parameters(String field, Function<Object, Object> mapper) {
        return (value, fieldOverride, target) -> putPath(target.parameters(),
            resolvedField(field, fieldOverride),
            value == null ? null : mapper.apply(value));
    }

    static ParameterMappingApplicator options(String field) {
        return (value, fieldOverride, target) ->
            putPath(target.options(), resolvedField(field, fieldOverride), value);
    }

    static ParameterMappingApplicator rootObject(String objectField, String field) {
        return (value, fieldOverride, target) -> {
            if (value == null) {
                return;
            }
            if (fieldOverride != null && !fieldOverride.isBlank()) {
                putPath(target.root(), fieldOverride, value);
                return;
            }
            @SuppressWarnings("unchecked")
            var nested = (java.util.Map<String, Object>) target.root().computeIfAbsent(objectField,
                ignored -> new java.util.LinkedHashMap<String, Object>());
            nested.put(field, value);
        };
    }

    private static String resolvedField(String defaultField, String fieldOverride) {
        return fieldOverride == null || fieldOverride.isBlank() ? defaultField : fieldOverride;
    }

    @SuppressWarnings("unchecked")
    private static void putPath(java.util.Map<String, Object> values, String path, Object value) {
        if (value == null) {
            return;
        }
        var segments = path.split("\\.");
        var target = values;
        for (int index = 0; index < segments.length - 1; index++) {
            target = (java.util.Map<String, Object>) target.computeIfAbsent(segments[index],
                ignored -> new java.util.LinkedHashMap<String, Object>());
        }
        target.put(segments[segments.length - 1], value);
    }

}
