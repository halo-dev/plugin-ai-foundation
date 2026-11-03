package run.halo.aifoundation.provider.mapping;

import java.util.List;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;

@Component
public class ParameterMappingValidator {

    private final ParameterMappingTemplateRegistry registry;
    private final ModelParameterCatalog catalog;

    public ParameterMappingValidator(ParameterMappingTemplateRegistry registry) {
        this(new ModelParameterCatalog(), registry);
    }

    @Autowired
    public ParameterMappingValidator(ModelParameterCatalog catalog,
        ParameterMappingTemplateRegistry registry) {
        this.catalog = catalog;
        this.registry = registry;
    }

    public void normalize(ModelParameterMappings mappings) {
        catalog.selections(mappings).forEach(entry -> {
            var selection = entry.selection();
            if (selection.getMode() == null) {
                selection.setMode(ModelParameterMappings.Mode.INHERIT);
            }
            if (selection.getTemplate() != null) {
                selection.setTemplate(selection.getTemplate().trim());
            }
            if (selection.getField() != null) {
                var field = selection.getField().trim();
                selection.setField(field.isEmpty() ? null : field);
            }
            normalizeReasoningMapping(selection.getReasoningMapping());
        });
    }

    public void validateProvider(ModelParameterMappings mappings, AiProviderType providerType) {
        if (mappings == null) {
            return;
        }
        normalize(mappings);
        var supportedAdapters = providerType.getSupportedAdapterTypes() != null
            ? providerType.getSupportedAdapterTypes() : List.<AdapterType>of();
        for (var entry : catalog.selections(mappings)) {
            validateSelection(entry, supportedAdapters, null);
        }
    }

    public void validateModel(ModelParameterMappings mappings, ModelType modelType,
        AdapterType adapterType) {
        if (mappings == null) {
            return;
        }
        normalize(mappings);
        rejectIrrelevantDomains(mappings, modelType);
        if (adapterType == null) {
            throw new IllegalArgumentException("adapterType is required for parameter mappings");
        }
        for (var entry : catalog.selections(mappings)) {
            validateSelection(entry, List.of(adapterType), modelType);
        }
    }

    public void validateConstraints(ModelParameterConstraints constraints) {
        if (constraints == null) {
            return;
        }
        positive("contextTokens", constraints.getContextTokens());
        positive("inputTokens", constraints.getInputTokens());
        positive("outputTokens", constraints.getOutputTokens());
        validateValues("toolChoices", constraints.getToolChoices());
        validateValues("serviceTiers", constraints.getServiceTiers());
        var reasoning = constraints.getReasoning();
        if (reasoning != null) {
            validateValues("reasoning.efforts", reasoning.getEfforts());
            var minimum = reasoning.getBudgetMinimum();
            var maximum = reasoning.getBudgetMaximum();
            if (minimum != null && minimum < 0) {
                throw new IllegalArgumentException("reasoning.budgetMinimum must not be negative");
            }
            positive("reasoning.budgetMaximum", maximum);
            if (minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalArgumentException("reasoning budget minimum exceeds maximum");
            }
        }
    }

    private void positive(String name, Long value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private void validateValues(String name, List<String> values) {
        if (values != null && (values.size() > 64 || values.stream()
            .anyMatch(value -> !StringUtils.hasText(value) || value.length() > 128))) {
            throw new IllegalArgumentException(name + " requires at most 64 nonempty values");
        }
    }

    private void validateSelection(ModelParameterCatalog.ConfiguredSelection entry,
        List<AdapterType> adapters, ModelType modelType) {
        var selection = entry.selection();
        var mode = selection.getMode();
        if (mode != ModelParameterMappings.Mode.TEMPLATE) {
            if (StringUtils.hasText(selection.getTemplate())
                || StringUtils.hasText(selection.getField())
                || selection.getReasoningMapping() != null) {
                throw invalid(entry, mode + " mapping must not contain template configuration");
            }
            return;
        }
        if (!StringUtils.hasText(selection.getTemplate())) {
            throw invalid(entry, "TEMPLATE mapping requires template");
        }
        var descriptor = registry.get(selection.getTemplate());
        if (descriptor == null) {
            throw invalid(entry, "Unknown template: " + selection.getTemplate());
        }
        if (descriptor.parameter() != entry.parameter()) {
            throw invalid(entry, "Template is not compatible with this parameter: "
                + descriptor.id());
        }
        if (modelType != null && entry.definition().modelType() != modelType) {
            throw invalid(entry, "Template is not compatible with model type: " + modelType);
        }
        if (adapters.stream().noneMatch(descriptor.adapterTypes()::contains)) {
            throw invalid(entry, "Template is not compatible with adapter: " + adapters);
        }
        if (entry.parameter() == ModelParameter.REASONING
            && StringUtils.hasText(selection.getField())) {
            throw invalid(entry, "Reasoning fields must be configured per intent");
        }
        validateField(entry, entry.selection().getField());
        validateConfiguration(entry, descriptor);
    }

    private void validateField(ModelParameterCatalog.ConfiguredSelection entry, String field) {
        if (!StringUtils.hasText(field)) {
            return;
        }
        if (field.length() > 128
            || !field.matches("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*){0,3}")) {
            throw invalid(entry,
                "field must be a dotted identifier with at most four segments");
        }
    }

    private void validateConfiguration(ModelParameterCatalog.ConfiguredSelection entry,
        ParameterMappingTemplateDescriptor descriptor) {
        var reasoning = entry.selection().getReasoningMapping();
        if (descriptor.configurationType()
            == ParameterMappingTemplateDescriptor.ConfigurationType.REASONING_MAPPING) {
            if (reasoning == null) {
                return;
            }
            var configured = 0;
            configured += validateReasoningValue(entry, descriptor, reasoning.getEnabled());
            configured += validateReasoningValue(entry, descriptor, reasoning.getDisabled());
            configured += validateReasoningValue(entry, descriptor, reasoning.getLow());
            configured += validateReasoningValue(entry, descriptor, reasoning.getMedium());
            configured += validateReasoningValue(entry, descriptor, reasoning.getHigh());
            if (configured == 0) {
                throw invalid(entry, "Reasoning mapping must configure at least one intent");
            }
            return;
        }
        if (reasoning != null) {
            throw invalid(entry, "Template does not accept reasoning intent configuration");
        }
    }

    private int validateReasoningValue(ModelParameterCatalog.ConfiguredSelection entry,
        ParameterMappingTemplateDescriptor descriptor,
        ModelParameterMappings.ReasoningValueMapping value) {
        if (value == null) {
            return 0;
        }
        validateReasoningScalar(entry, descriptor, value);
        var fields = new HashSet<String>();
        fields.add(value.getField());
        if (value.getAdditionalValues() != null) {
            if (value.getAdditionalValues().size() > 8) {
                throw invalid(entry, "Reasoning intent supports at most eight additional fields");
            }
            for (var additional : value.getAdditionalValues()) {
                if (additional == null || additional.getAdditionalValues() != null
                    && !additional.getAdditionalValues().isEmpty()) {
                    throw invalid(entry, "Additional reasoning fields must be scalar values");
                }
                validateReasoningScalar(entry, descriptor, additional);
                if (fields.stream().anyMatch(field -> field.equals(additional.getField())
                    || field.startsWith(additional.getField() + ".")
                    || additional.getField().startsWith(field + "."))) {
                    throw invalid(entry, "Reasoning fields must not overlap");
                }
                fields.add(additional.getField());
            }
        }
        return 1;
    }

    private void validateReasoningScalar(ModelParameterCatalog.ConfiguredSelection entry,
        ParameterMappingTemplateDescriptor descriptor,
        ModelParameterMappings.ReasoningValueMapping value) {
        if (!StringUtils.hasText(value.getField())) {
            throw invalid(entry, "Each reasoning intent requires field, value type, and value");
        }
        if (value.getValueType() == null) {
            throw invalid(entry, "Each reasoning intent requires field, value type, and value");
        }
        if (value.getValue() == null) {
            throw invalid(entry, "Each reasoning intent requires field, value type, and value");
        }
        if (value.getValue().isBlank()) {
            throw invalid(entry, "Each reasoning intent requires field, value type, and value");
        }
        validateField(entry, value.getField());
        if ("reasoning.ollama-think".equals(descriptor.id())
            && !"think".equals(value.getField())) {
            throw invalid(entry, "Ollama reasoning field must be think");
        }
        try {
            var typed = value.typedValue();
            if (typed instanceof Double number && !Double.isFinite(number)) {
                throw new NumberFormatException("non-finite decimal");
            }
        } catch (IllegalArgumentException error) {
            throw invalid(entry, "Reasoning value does not match " + value.getValueType());
        }
        if (value.getValueType() != ModelParameterMappings.ValueType.BOOLEAN) {
            return;
        }
        if (!"true".equals(value.getValue()) && !"false".equals(value.getValue())) {
            throw invalid(entry, "Boolean reasoning values must be true or false");
        }
    }

    private void normalizeReasoningMapping(ModelParameterMappings.ReasoningMapping mapping) {
        if (mapping == null) {
            return;
        }
        mapping.setEnabled(normalizeReasoningValue(mapping.getEnabled()));
        mapping.setDisabled(normalizeReasoningValue(mapping.getDisabled()));
        mapping.setLow(normalizeReasoningValue(mapping.getLow()));
        mapping.setMedium(normalizeReasoningValue(mapping.getMedium()));
        mapping.setHigh(normalizeReasoningValue(mapping.getHigh()));
    }

    private ModelParameterMappings.ReasoningValueMapping normalizeReasoningValue(
        ModelParameterMappings.ReasoningValueMapping value) {
        if (value == null) {
            return null;
        }
        if (value.getField() != null) {
            value.setField(value.getField().trim());
        }
        if (value.getValue() != null) {
            value.setValue(value.getValue().trim());
        }
        if (value.getAdditionalValues() != null) {
            // Nested additions are rejected by validation, not traversed recursively.
            value.getAdditionalValues().stream().filter(java.util.Objects::nonNull)
                .forEach(additional -> {
                    if (additional.getField() != null) {
                        additional.setField(additional.getField().trim());
                    }
                    if (additional.getValue() != null) {
                        additional.setValue(additional.getValue().trim());
                    }
                });
        }
        if (!StringUtils.hasText(value.getField()) && value.getValueType() == null
            && !StringUtils.hasText(value.getValue())
            && (value.getAdditionalValues() == null || value.getAdditionalValues().isEmpty())) {
            return null;
        }
        return value;
    }

    private void rejectIrrelevantDomains(ModelParameterMappings mappings, ModelType modelType) {
        if (modelType == null) {
            throw new IllegalArgumentException("modelType is required for parameter mappings");
        }
        for (var domain : catalog.presentDomains(mappings)) {
            if (domain.getModelType() != modelType) {
                throw new IllegalArgumentException(domain.getValue()
                    + " parameter mappings require " + domain.getModelType().getValue() + " model");
            }
        }
    }

    private IllegalArgumentException invalid(ModelParameterCatalog.ConfiguredSelection entry,
        String detail) {
        return new IllegalArgumentException("Invalid parameter mapping " + entry.parameter().name()
            + ": " + detail);
    }
}