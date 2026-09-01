package run.halo.aifoundation.provider.protocol.responses;

import java.util.Set;

/** Structural inspection helpers for canonical Responses input items. */
public final class ResponsesInputs {

    private ResponsesInputs() {
    }

    public static boolean containsType(Object value, Set<String> types) {
        if (value instanceof java.util.Map<?, ?> map) {
            if (isTargetType(map.get("type"), types)) {
                return true;
            }
            return map.values().stream().anyMatch(item -> containsType(item, types));
        }
        if (!(value instanceof Iterable<?> values)) {
            return false;
        }
        for (var item : values) {
            if (containsType(item, types)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetType(Object value, Set<String> types) {
        if (value == null) {
            return false;
        }
        return types.contains(value);
    }
}
