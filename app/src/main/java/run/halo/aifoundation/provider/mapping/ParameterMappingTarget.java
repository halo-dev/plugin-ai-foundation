package run.halo.aifoundation.provider.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable adapter-owned request sections used by registered template applicators. */
public final class ParameterMappingTarget {
    private final Map<String, Object> root = new LinkedHashMap<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final Map<String, Object> options = new LinkedHashMap<>();

    public Map<String, Object> root() {
        return root;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public Map<String, Object> options() {
        return options;
    }
}
