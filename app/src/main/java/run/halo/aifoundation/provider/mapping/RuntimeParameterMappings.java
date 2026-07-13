package run.halo.aifoundation.provider.mapping;

import java.util.Map;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.ModelParameterMappings;

/**
 * Executable, identity-aware view of effective mappings used by model runtimes.
 */
public final class RuntimeParameterMappings {

    private final EffectiveParameterMappings effectiveMappings;
    private final ParameterMappingTemplateRegistry templates;
    private final String modelName;
    private final String providerName;

    public RuntimeParameterMappings(EffectiveParameterMappings effectiveMappings,
        ParameterMappingTemplateRegistry templates, String modelName, String providerName) {
        this.effectiveMappings = effectiveMappings != null
            ? effectiveMappings : EffectiveParameterMappings.empty();
        this.templates = templates != null ? templates : new ParameterMappingTemplateRegistry();
        this.modelName = modelName;
        this.providerName = providerName;
    }

    public static RuntimeParameterMappings empty() {
        return new RuntimeParameterMappings(EffectiveParameterMappings.empty(), null, null, null);
    }

    public EffectiveParameterMappings.EffectiveMapping get(ModelParameter parameter) {
        return effectiveMappings.get(parameter);
    }

    public Map<ModelParameter, EffectiveParameterMappings.EffectiveMapping> values() {
        return effectiveMappings.values();
    }

    public boolean isEmpty() {
        return effectiveMappings.values().isEmpty();
    }

    public boolean isUnsupported(ModelParameter parameter) {
        var mapping = get(parameter);
        return mapping != null && mapping.mode() == ModelParameterMappings.Mode.UNSUPPORTED;
    }

    public boolean apply(ModelParameter parameter, Object value, ParameterMappingTarget target) {
        var mapping = get(parameter);
        if (value == null || mapping == null
            || mapping.mode() != ModelParameterMappings.Mode.TEMPLATE) {
            return false;
        }
        var descriptor = templates.get(mapping.template());
        if (descriptor == null) {
            return false;
        }
        descriptor.applicator().apply(value, mapping.field(), target);
        return true;
    }

    public boolean canApplyReasoning(ReasoningOptions reasoning) {
        return reasoningValue(reasoning) != null;
    }

    public boolean applyReasoning(ReasoningOptions reasoning, ParameterMappingTarget target) {
        var mapping = get(ModelParameter.REASONING);
        if (mapping == null || mapping.mode() != ModelParameterMappings.Mode.TEMPLATE) {
            return false;
        }
        var descriptor = templates.get(mapping.template());
        var value = reasoningValue(reasoning);
        if (descriptor == null || value == null) {
            return false;
        }
        descriptor.applicator().apply(value.typedValue(), value.getField(), target);
        return true;
    }

    private ModelParameterMappings.ReasoningValueMapping reasoningValue(
        ReasoningOptions reasoning) {
        if (reasoning == null || !reasoning.isExplicit()) {
            return null;
        }
        var mapping = get(ModelParameter.REASONING);
        if (mapping == null || mapping.mode() != ModelParameterMappings.Mode.TEMPLATE) {
            return null;
        }
        var descriptor = templates.get(mapping.template());
        if (descriptor == null) {
            return null;
        }
        var values = mapping.reasoningMapping() != null
            ? mapping.reasoningMapping() : descriptor.defaultReasoningMapping();
        if (values == null) {
            return null;
        }
        if (reasoning.getEffort() != null) {
            return switch (reasoning.getEffort()) {
                case LOW -> values.getLow();
                case MEDIUM -> values.getMedium();
                case HIGH -> values.getHigh();
            };
        }
        return switch (reasoning.getMode()) {
            case ENABLED -> values.getEnabled();
            case DISABLED -> values.getDisabled();
            case DEFAULT -> null;
        };
    }

    public UnsupportedParameterDiagnostic unsupportedDiagnostic(ModelParameter parameter) {
        return new UnsupportedParameterDiagnostic(parameter, modelName, providerName);
    }
}
