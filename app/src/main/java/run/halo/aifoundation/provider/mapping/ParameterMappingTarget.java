package run.halo.aifoundation.provider.mapping;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable adapter-owned request sections used by registered template applicators. */
public final class ParameterMappingTarget {
    private final Map<String, Object> root = new LinkedHashMap<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final Map<String, Object> options = new LinkedHashMap<>();
    private final Set<ModelParameter> appliedParameters =
        EnumSet.noneOf(ModelParameter.class);

    public Map<String, Object> root() {
        return root;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public Map<String, Object> options() {
        return options;
    }

    /** Records the portable parameter whose mapping populated this target. */
    public void recordAppliedParameter(ModelParameter parameter) {
        appliedParameters.add(Objects.requireNonNull(parameter, "parameter must not be null"));
    }

    /**
     * Returns the portable parameters that produced fields in this target.
     *
     * <p>The metadata lets a protocol adapter replace only the corresponding native defaults,
     * instead of deleting unrelated administrator-defined options.</p>
     */
    public Set<ModelParameter> appliedParameters() {
        return Set.copyOf(appliedParameters);
    }
}
