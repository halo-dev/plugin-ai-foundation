package run.halo.aifoundation.provider.mapping;

import java.util.Set;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.support.AdapterType;

public record ParameterMappingTemplateDescriptor(
    String id,
    String displayName,
    String description,
    String defaultField,
    ModelParameter parameter,
    Set<AdapterType> adapterTypes,
    ConfigurationType configurationType,
    ModelParameterMappings.ReasoningMapping defaultReasoningMapping,
    ParameterMappingApplicator applicator
) {
    public ParameterMappingTemplateDescriptor(String id, String displayName, String description,
        ModelParameter parameter, Set<AdapterType> adapterTypes,
        ConfigurationType configurationType, ParameterMappingApplicator applicator) {
        this(id, displayName, description, null, parameter, adapterTypes, configurationType,
            null, applicator);
    }

    public enum ConfigurationType {
        NONE,
        REASONING_MAPPING
    }

    public ParameterMappingTemplateDescriptor {
        adapterTypes = adapterTypes == null ? Set.of() : Set.copyOf(adapterTypes);
        configurationType = configurationType == null ? ConfigurationType.NONE : configurationType;
        if (applicator == null) {
            throw new IllegalArgumentException("parameter mapping applicator is required");
        }
    }
}
