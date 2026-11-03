package run.halo.aifoundation.provider.mapping;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.service.model.ModelResolution;

@Component
public class EffectiveParameterMappingResolver {

    private final ModelParameterCatalog catalog;
    private final ParameterMappingTemplateRegistry templates;

    public EffectiveParameterMappingResolver() {
        this(new ModelParameterCatalog());
    }

    public EffectiveParameterMappingResolver(ModelParameterCatalog catalog) {
        this(catalog, new ParameterMappingTemplateRegistry());
    }

    @Autowired
    public EffectiveParameterMappingResolver(ModelParameterCatalog catalog,
        ParameterMappingTemplateRegistry templates) {
        this.catalog = catalog;
        this.templates = templates;
    }

    public EffectiveParameterMappings resolve(ModelResolution resolution) {
        var values = resolveMappings(resolution);
        var constraints = resolveConstraints(resolution, values);
        var manual = resolution.model().getSpec().getParameterConstraints();
        constrain(values, ModelParameter.TEMPERATURE, constraints.getTemperature(),
            manual != null && manual.getTemperature() != null);
        constrain(values, ModelParameter.TOP_P, constraints.getTopP(),
            manual != null && manual.getTopP() != null);
        constrain(values, ModelParameter.TOP_K, constraints.getTopK(),
            manual != null && manual.getTopK() != null);
        if (constraints.getReasoning() != null) {
            constrain(values, ModelParameter.REASONING, constraints.getReasoning().getSupported(),
                manual != null && manual.getReasoning() != null
                    && manual.getReasoning().getSupported() != null);
            constrainReasoning(values, constraints.getReasoning());
        }
        return new EffectiveParameterMappings(values);
    }

    private EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping> resolveMappings(
        ModelResolution resolution) {
        var modelType = resolution.model().getSpec().getModelType();
        var values = new EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping>(
            ModelParameter.class);
        defaultMappings(resolution, modelType).forEach((parameter, mapping) -> {
            if (catalog.definition(parameter).modelType() == modelType) {
                values.put(parameter, new EffectiveParameterMappings.EffectiveMapping(
                    mapping.mode(), mapping.template(), null, null,
                    EffectiveParameterMappings.Source.BUILT_IN));
            }
        });
        for (var definition : catalog.definitionsFor(List.of(modelType))) {
            if (!values.containsKey(definition.parameter())) {
                values.put(definition.parameter(),
                    unsupported(EffectiveParameterMappings.Source.BUILT_IN));
            }
        }
        var profile = applicableCatalogProfile(resolution);
        if (profile != null) {
            overlay(values, profile.getMappings(), EffectiveParameterMappings.Source.CATALOG,
                modelType);
        }
        overlay(values, resolution.provider().getSpec().getParameterMappings(),
            EffectiveParameterMappings.Source.PROVIDER, modelType);
        overlay(values, resolution.model().getSpec().getParameterMappings(),
            EffectiveParameterMappings.Source.MODEL, modelType);
        return values;
    }

    /** Resolves the same model configuration used by runtime and Console consumers. */
    public ModelParameterProfile resolveProfile(ModelResolution resolution) {
        var effective = resolve(resolution);
        var result = new ModelParameterProfile();
        result.setModelId(resolution.modelId());
        result.setProviderType(resolution.providerTypeName());
        result.setProviderBaseUrl(providerBaseUrl(resolution));
        result.setAdapterType(defaultMappingAdapter(resolution,
            resolution.model().getSpec().getModelType()));
        var mappings = new ModelParameterMappings();
        effective.values().forEach((parameter, mapping) -> {
            var selection = new ModelParameterMappings.Selection();
            selection.setMode(mapping.mode());
            selection.setTemplate(mapping.template());
            selection.setField(mapping.field());
            var template = templates.get(mapping.template());
            selection.setReasoningMapping(mapping.reasoningMapping() != null
                ? mapping.reasoningMapping() : template != null ? template.defaultReasoningMapping() : null);
            catalog.definition(parameter).write(mappings, selection);
        });
        result.setMappings(mappings);
        result.setConstraints(resolveConstraints(resolution, resolveMappings(resolution)));
        return result;
    }

    /** Returns only the captured catalog profile that still matches this model and endpoint. */
    public ModelParameterProfile applicableCatalogProfile(ModelResolution resolution) {
        var endpointPath = resolution.provider().getSpec().getChatEndpointPath();
        if (endpointPath != null && !endpointPath.isBlank()) {
            return null;
        }
        var profile = resolution.model().getSpec().getCatalogParameters();
        if (profile == null || profile.getModelId() == null || profile.getProviderType() == null
            || profile.getAdapterType() == null) {
            return null;
        }
        return Objects.equals(profile.getModelId(), resolution.modelId())
            && Objects.equals(profile.getProviderType(), resolution.providerTypeName())
            && (profile.getProviderBaseUrl() == null
                || Objects.equals(profile.getProviderBaseUrl(), providerBaseUrl(resolution)))
            && profile.getAdapterType() == defaultMappingAdapter(resolution,
                resolution.model().getSpec().getModelType()) ? profile : null;
    }

    private String providerBaseUrl(ModelResolution resolution) {
        var configured = resolution.provider().getSpec().getBaseUrl();
        return configured != null && !configured.isBlank()
            ? configured : resolution.providerType().getDefaultBaseUrl();
    }

    public ModelParameterConstraints resolveConstraints(ModelResolution resolution) {
        return resolveConstraints(resolution, resolveMappings(resolution));
    }

    private ModelParameterConstraints resolveConstraints(ModelResolution resolution,
        java.util.Map<ModelParameter, EffectiveParameterMappings.EffectiveMapping> mappings) {
        var profile = applicableCatalogProfile(resolution);
        var constraints = mergeConstraints(null, profile != null ? profile.getConstraints() : null);
        // An administrator mapping is an explicit override of the catalog's model facts.
        mappings.forEach((parameter, mapping) -> {
            if (mapping.source() != EffectiveParameterMappings.Source.MODEL
                && mapping.source() != EffectiveParameterMappings.Source.PROVIDER) {
                return;
            }
            switch (parameter) {
                case TEMPERATURE -> constraints.setTemperature(null);
                case TOP_P -> constraints.setTopP(null);
                case TOP_K -> constraints.setTopK(null);
                case MAX_OUTPUT_TOKENS -> constraints.setOutputTokens(null);
                case REASONING -> constraints.setReasoning(null);
                default -> { }
            }
        });
        return mergeConstraints(constraints, resolution.model().getSpec().getParameterConstraints());
    }

    private void constrain(
        EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping> mappings,
        ModelParameter parameter, Boolean supported, boolean manual) {
        if (Boolean.FALSE.equals(supported) && mappings.containsKey(parameter)) {
            mappings.put(parameter, unsupported(manual ? EffectiveParameterMappings.Source.MODEL
                : EffectiveParameterMappings.Source.CATALOG));
        }
    }

    private void constrainReasoning(
        EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping> mappings,
        ModelParameterConstraints.Reasoning constraints) {
        var current = mappings.get(ModelParameter.REASONING);
        if (current == null || current.mode() != ModelParameterMappings.Mode.TEMPLATE) {
            return;
        }
        var template = templates.get(current.template());
        var original = current.reasoningMapping() != null ? current.reasoningMapping()
            : template != null ? template.defaultReasoningMapping() : null;
        if (original == null) {
            return;
        }
        var filtered = new ModelParameterMappings.ReasoningMapping();
        if (!Boolean.FALSE.equals(constraints.getToggle())) {
            filtered.setEnabled(allowedReasoningValue(original.getEnabled(), constraints));
            filtered.setDisabled(allowedReasoningValue(original.getDisabled(), constraints));
        }
        filtered.setLow(allowedReasoningValue(original.getLow(), constraints));
        filtered.setMedium(allowedReasoningValue(original.getMedium(), constraints));
        filtered.setHigh(allowedReasoningValue(original.getHigh(), constraints));
        mappings.put(ModelParameter.REASONING, new EffectiveParameterMappings.EffectiveMapping(
            current.mode(), current.template(), current.field(), filtered, current.source()));
    }

    private ModelParameterMappings.ReasoningValueMapping allowedReasoningValue(
        ModelParameterMappings.ReasoningValueMapping value,
        ModelParameterConstraints.Reasoning constraints) {
        if (value == null || !RuntimeParameterMappings.reasoningFieldAllowed(value.getField(),
            value.getValue(), constraints)) {
            return null;
        }
        if (value.getAdditionalValues() != null && value.getAdditionalValues().stream()
            .anyMatch(item -> item == null || !RuntimeParameterMappings.reasoningFieldAllowed(
                item.getField(), item.getValue(), constraints))) {
            return null;
        }
        return value;
    }

    private ModelParameterConstraints mergeConstraints(ModelParameterConstraints base,
        ModelParameterConstraints override) {
        var result = new ModelParameterConstraints();
        if (base == null) {
            base = new ModelParameterConstraints();
        }
        if (override == null) {
            override = new ModelParameterConstraints();
        }
        result.setTemperature(prefer(override.getTemperature(), base.getTemperature()));
        result.setTopP(prefer(override.getTopP(), base.getTopP()));
        result.setTopK(prefer(override.getTopK(), base.getTopK()));
        result.setToolCalling(prefer(override.getToolCalling(), base.getToolCalling()));
        result.setStructuredOutput(prefer(override.getStructuredOutput(), base.getStructuredOutput()));
        result.setToolStrict(prefer(override.getToolStrict(), base.getToolStrict()));
        result.setStore(prefer(override.getStore(), base.getStore()));
        result.setStreamUsage(prefer(override.getStreamUsage(), base.getStreamUsage()));
        result.setPromptCacheKey(prefer(override.getPromptCacheKey(), base.getPromptCacheKey()));
        result.setContextTokens(prefer(override.getContextTokens(), base.getContextTokens()));
        result.setInputTokens(prefer(override.getInputTokens(), base.getInputTokens()));
        result.setOutputTokens(prefer(override.getOutputTokens(), base.getOutputTokens()));
        result.setToolChoices(prefer(override.getToolChoices(), base.getToolChoices()));
        result.setServiceTiers(prefer(override.getServiceTiers(), base.getServiceTiers()));
        if (base.getReasoning() != null || override.getReasoning() != null) {
            var left = base.getReasoning() != null ? base.getReasoning()
                : new ModelParameterConstraints.Reasoning();
            var right = override.getReasoning() != null ? override.getReasoning()
                : new ModelParameterConstraints.Reasoning();
            var reasoning = new ModelParameterConstraints.Reasoning();
            reasoning.setSupported(prefer(right.getSupported(), left.getSupported()));
            reasoning.setToggle(prefer(right.getToggle(), left.getToggle()));
            reasoning.setEfforts(prefer(right.getEfforts(), left.getEfforts()));
            reasoning.setBudgetMinimum(prefer(right.getBudgetMinimum(), left.getBudgetMinimum()));
            reasoning.setBudgetMaximum(prefer(right.getBudgetMaximum(), left.getBudgetMaximum()));
            result.setReasoning(reasoning);
        }
        return result;
    }

    private <T> T prefer(T override, T inherited) {
        return override != null ? override : inherited;
    }

    private java.util.Map<ModelParameter, DefaultParameterMapping> defaultMappings(
        ModelResolution resolution, run.halo.aifoundation.provider.support.ModelType modelType) {
        var providerType = resolution.providerType();
        var adapterType = defaultMappingAdapter(resolution, modelType);
        if (adapterType == null) {
            return providerType.getDefaultParameterMappings();
        }
        return providerType.getDefaultParameterMappings(adapterType);
    }

    private run.halo.aifoundation.provider.support.AdapterType defaultMappingAdapter(
        ModelResolution resolution, run.halo.aifoundation.provider.support.ModelType modelType) {
        var providerType = resolution.providerType();
        var configured = resolution.model().getSpec().getAdapterType();
        if (configured == null) {
            return providerType.recommendAdapterType(modelType).orElse(null);
        }
        var supported = providerType.getSupportedAdapterTypes();
        if (supported == null) {
            return configured;
        }
        if (supported.contains(configured)) {
            return configured;
        }
        return providerType.recommendAdapterType(modelType).orElse(null);
    }

    private void overlay(
        EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping> target,
        ModelParameterMappings mappings, EffectiveParameterMappings.Source source,
        run.halo.aifoundation.provider.support.ModelType modelType) {
        for (var entry : catalog.selections(mappings)) {
            if (entry.definition().modelType() != modelType
                || entry.selection().getMode() == null
                || entry.selection().getMode() == ModelParameterMappings.Mode.INHERIT) {
                continue;
            }
            var selection = entry.selection();
            target.put(entry.parameter(), new EffectiveParameterMappings.EffectiveMapping(
                selection.getMode(), selection.getTemplate(), selection.getField(),
                selection.getReasoningMapping(),
                source));
        }
    }

    private EffectiveParameterMappings.EffectiveMapping unsupported(
        EffectiveParameterMappings.Source source) {
        return new EffectiveParameterMappings.EffectiveMapping(
            ModelParameterMappings.Mode.UNSUPPORTED, null, null, null, source);
    }
}