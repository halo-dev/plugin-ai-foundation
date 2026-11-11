package run.halo.aifoundation.provider.mapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;

/** Executable, identity-aware view of effective mappings used by model runtimes. */
public final class RuntimeParameterMappings {

    private final EffectiveParameterMappings effectiveMappings;
    private final ParameterMappingTemplateRegistry templates;
    private final String modelName;
    private final String providerName;

    public RuntimeParameterMappings(
            EffectiveParameterMappings effectiveMappings,
            ParameterMappingTemplateRegistry templates,
            String modelName,
            String providerName) {
        this.effectiveMappings = effectiveMappings != null ? effectiveMappings : EffectiveParameterMappings.empty();
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
        if (value == null) {
            return false;
        }
        if (mapping == null) {
            return false;
        }
        if (mapping.mode() != ModelParameterMappings.Mode.TEMPLATE) {
            return false;
        }
        var descriptor = templates.get(mapping.template());
        if (descriptor == null) {
            return false;
        }
        descriptor.applicator().apply(value, mapping.field(), target);
        target.recordAppliedParameter(parameter);
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
        // Apply to an isolated target first so a malformed compound mapping cannot partly write.
        var staged = new ParameterMappingTarget();
        for (var item : validatedReasoningValues(value)) {
            descriptor.applicator().apply(typedReasoningValue(item), item.getField(), staged);
        }
        mergeMappedValues(target.root(), staged.root());
        mergeMappedValues(target.parameters(), staged.parameters());
        mergeMappedValues(target.options(), staged.options());
        target.recordAppliedParameter(ModelParameter.REASONING);
        return true;
    }

    public void validateReasoning(ReasoningOptions reasoning, ModelParameterConstraints.Reasoning constraints) {
        var value = reasoningValue(reasoning);
        if (value == null || constraints == null) {
            return;
        }
        for (var item : validatedReasoningValues(value)) {
            var field = item.getField();
            if (!reasoningFieldAllowed(field, item.getValue(), constraints)) {
                throw new IllegalArgumentException("reasoning value for " + field + " is not supported by this model");
            }
            if (!isReasoningBudgetField(field)) {
                continue;
            }
            var typed = typedReasoningValue(item);
            if (!(typed instanceof Number budget)) {
                throw new IllegalArgumentException("reasoning budget must be numeric");
            }
            validateBudget(budget, constraints);
        }
    }

    public static void validateBudget(long budget, ModelParameterConstraints.Reasoning constraints) {
        validateBudget(BigDecimal.valueOf(budget), constraints);
    }

    public static void validateBudget(Number budget, ModelParameterConstraints.Reasoning constraints) {
        if (constraints == null) {
            return;
        }
        if (Boolean.FALSE.equals(constraints.getSupported())) {
            throw new IllegalArgumentException("reasoning budget is not supported by this model");
        }
        if (constraints.getBudgetMinimum() == null && constraints.getBudgetMaximum() == null) {
            return;
        }
        // Preserve arbitrary-precision native numbers so narrowing cannot hide a violated limit.
        var value = new BigDecimal(budget.toString());
        if (constraints.getBudgetMinimum() != null
                && value.compareTo(BigDecimal.valueOf(constraints.getBudgetMinimum())) < 0) {
            throw new IllegalArgumentException("reasoning budget must be at least " + constraints.getBudgetMinimum());
        }
        if (constraints.getBudgetMaximum() != null
                && value.compareTo(BigDecimal.valueOf(constraints.getBudgetMaximum())) > 0) {
            throw new IllegalArgumentException("reasoning budget must not exceed " + constraints.getBudgetMaximum());
        }
    }

    /** Tests native controls only; unrecognized fields and absent facts remain unknown. */
    public static boolean reasoningFieldAllowed(
            String field, Object value, ModelParameterConstraints.Reasoning constraints) {
        if (constraints == null || field == null) {
            return true;
        }
        var effort = isReasoningEffortField(field, value);
        var toggle = isReasoningToggleField(field, value);
        if (!effort && !toggle) {
            return true;
        }
        if (Boolean.FALSE.equals(constraints.getSupported())) {
            return false;
        }
        if (toggle && Boolean.FALSE.equals(constraints.getToggle())) {
            return false;
        }
        return !effort
                || constraints.getEfforts() == null
                || constraints.getEfforts().contains(String.valueOf(value));
    }

    private static boolean isReasoningEffortField(String field, Object value) {
        return switch (field) {
            case "reasoning_effort", "reasoning.effort", "output_config.effort" -> true;
            // Ollama accepts either a boolean switch or a named effort in the same field.
            case "think" -> value instanceof String && !List.of("true", "false").contains(value);
            default -> false;
        };
    }

    private static boolean isReasoningToggleField(String field, Object value) {
        return switch (field) {
            case "enable_thinking", "thinking.type", "reasoning.enabled" -> true;
            case "think" -> !isReasoningEffortField(field, value);
            default -> false;
        };
    }

    private static boolean isReasoningBudgetField(String field) {
        return field != null
                && (field.endsWith("budget_tokens")
                        || field.endsWith("thinking_budget")
                        || field.equals("reasoning.max_tokens"));
    }

    private List<ModelParameterMappings.ReasoningValueMapping> validatedReasoningValues(
            ModelParameterMappings.ReasoningValueMapping value) {
        var values = new ArrayList<ModelParameterMappings.ReasoningValueMapping>();
        values.add(value);
        if (value.getAdditionalValues() != null) {
            if (value.getAdditionalValues().size() > 8) {
                throw new IllegalArgumentException("reasoning mapping permits at most 8 additional values");
            }
            values.addAll(value.getAdditionalValues());
        }
        var fields = new HashSet<String>();
        for (var index = 0; index < values.size(); index++) {
            var item = values.get(index);
            if (item == null || item.getField() == null || item.getField().isBlank()) {
                throw new IllegalArgumentException("reasoning mapping field must not be blank");
            }
            if (index > 0
                    && item.getAdditionalValues() != null
                    && !item.getAdditionalValues().isEmpty()) {
                throw new IllegalArgumentException("reasoning mapping additional values cannot be nested");
            }
            if (fields.stream()
                    .anyMatch(field -> field.equals(item.getField())
                            || field.startsWith(item.getField() + ".")
                            || item.getField().startsWith(field + "."))) {
                throw new IllegalArgumentException("reasoning mapping fields must not overlap: " + item.getField());
            }
            fields.add(item.getField());
            typedReasoningValue(item);
        }
        return values;
    }

    private Object typedReasoningValue(ModelParameterMappings.ReasoningValueMapping value) {
        if (value.getValueType() == null || value.getValue() == null) {
            throw new IllegalArgumentException("reasoning mapping value and type are required");
        }
        if (value.getValueType() == ModelParameterMappings.ValueType.BOOLEAN
                && !List.of("true", "false").contains(value.getValue())) {
            throw new IllegalArgumentException("reasoning mapping boolean must be true or false");
        }
        var typed = value.typedValue();
        if (typed instanceof Double number && !Double.isFinite(number)) {
            throw new IllegalArgumentException("reasoning mapping decimal must be finite");
        }
        return typed;
    }

    @SuppressWarnings("unchecked")
    private void mergeMappedValues(Map<String, Object> target, Map<String, Object> values) {
        values.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested && target.get(key) instanceof Map<?, ?> existing) {
                var merged = new LinkedHashMap<>((Map<String, Object>) existing);
                mergeMappedValues(merged, (Map<String, Object>) nested);
                target.put(key, merged);
            } else {
                target.put(key, value);
            }
        });
    }

    private ModelParameterMappings.ReasoningValueMapping reasoningValue(ReasoningOptions reasoning) {
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
        var values =
                mapping.reasoningMapping() != null ? mapping.reasoningMapping() : descriptor.defaultReasoningMapping();
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
