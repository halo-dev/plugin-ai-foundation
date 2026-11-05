package run.halo.aifoundation.provider.mapping;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappings.EffectiveMapping;
import run.halo.aifoundation.provider.support.AdapterProtocol;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
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
        applyConstraints(values, constraints,
            resolution.model().getSpec().getParameterConstraints());
        return new EffectiveParameterMappings(values);
    }

    private void applyConstraints(EnumMap<ModelParameter, EffectiveMapping> values,
        ModelParameterConstraints constraints, ModelParameterConstraints manual) {
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
    }

    private EnumMap<ModelParameter, EffectiveMapping> resolveMappings(
        ModelResolution resolution) {
        var modelType = resolution.model().getSpec().getModelType();
        var values = new EnumMap<ModelParameter, EffectiveMapping>(
            ModelParameter.class);
        defaultMappings(resolution, modelType).forEach((parameter, mapping) -> {
            if (catalog.definition(parameter).modelType() == modelType) {
                values.put(parameter, new EffectiveMapping(
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
        var effective = resolveMappings(resolution);
        var constraints = resolveConstraints(resolution, effective);
        applyConstraints(effective, constraints,
            resolution.model().getSpec().getParameterConstraints());
        var result = new ModelParameterProfile();
        result.setModelId(resolution.modelId());
        result.setProviderType(resolution.providerTypeName());
        result.setProviderBaseUrl(providerBaseUrl(resolution));
        result.setAdapterType(defaultMappingAdapter(resolution,
            resolution.model().getSpec().getModelType()));
        var mappings = new ModelParameterMappings();
        effective.forEach((parameter, mapping) -> {
            var selection = new ModelParameterMappings.Selection();
            selection.setMode(mapping.mode());
            selection.setTemplate(mapping.template());
            selection.setField(mapping.field());
            selection.setReasoningMapping(reasoningMapping(mapping));
            catalog.definition(parameter).write(mappings, selection);
        });
        result.setMappings(mappings);
        result.setConstraints(constraints);
        return result;
    }

    /** Returns only the captured catalog profile that still matches this model and endpoint. */
    public ModelParameterProfile applicableCatalogProfile(ModelResolution resolution) {
        var adapter = defaultMappingAdapter(resolution,
            resolution.model().getSpec().getModelType());
        var endpointPath = resolution.provider().getSpec().getChatEndpointPath();
        if (adapter != null && adapter.getProtocol() == AdapterProtocol.CHAT_COMPLETIONS
            && endpointPath != null && !endpointPath.isBlank()) {
            return null;
        }
        var profile = resolution.model().getSpec().getCatalogParameters();
        if (profile == null || profile.getModelId() == null || profile.getProviderType() == null
            || profile.getAdapterType() == null) {
            return null;
        }
        var matchesIdentity = Objects.equals(profile.getModelId(), resolution.modelId())
            && Objects.equals(profile.getProviderType(), resolution.providerTypeName())
            && Objects.equals(profile.getProviderBaseUrl(), providerBaseUrl(resolution))
            && profile.getAdapterType() == adapter;
        return matchesIdentity ? profile : null;
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
        Map<ModelParameter, EffectiveMapping> mappings) {
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
        var result = mergeConstraints(constraints,
            resolution.model().getSpec().getParameterConstraints());
        restrictToAdapterCapabilities(resolution, result);
        return result;
    }

    private void restrictToAdapterCapabilities(ModelResolution resolution,
        ModelParameterConstraints constraints) {
        if (resolution.model().getSpec().getModelType() != ModelType.LANGUAGE) {
            return;
        }
        var adapter = defaultMappingAdapter(resolution, ModelType.LANGUAGE);
        var supported = resolution.providerType().getSupportedFeatures(adapter);
        if (Boolean.TRUE.equals(constraints.getToolCalling())
            && !supported.contains(ModelFeature.TOOL_CALL)) {
            constraints.setToolCalling(false);
        }
        if (Boolean.TRUE.equals(constraints.getStructuredOutput())
            && !supported.contains(ModelFeature.STRUCTURED_OUTPUT)) {
            constraints.setStructuredOutput(false);
        }
        var reasoning = constraints.getReasoning();
        if (reasoning != null && Boolean.TRUE.equals(reasoning.getSupported())
            && !supported.contains(ModelFeature.REASONING)) {
            reasoning.setSupported(false);
        }
    }

    private void constrain(
        EnumMap<ModelParameter, EffectiveMapping> mappings,
        ModelParameter parameter, Boolean supported, boolean manual) {
        if (Boolean.FALSE.equals(supported) && mappings.containsKey(parameter)) {
            mappings.put(parameter, unsupported(manual ? EffectiveParameterMappings.Source.MODEL
                : EffectiveParameterMappings.Source.CATALOG));
        }
    }

    private void constrainReasoning(
        EnumMap<ModelParameter, EffectiveMapping> mappings,
        ModelParameterConstraints.Reasoning constraints) {
        var current = mappings.get(ModelParameter.REASONING);
        if (current == null || current.mode() != ModelParameterMappings.Mode.TEMPLATE) {
            return;
        }
        var original = reasoningMapping(current);
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
        mappings.put(ModelParameter.REASONING, new EffectiveMapping(
            current.mode(), current.template(), current.field(), filtered, current.source()));
    }

    private ModelParameterMappings.ReasoningMapping reasoningMapping(EffectiveMapping mapping) {
        if (mapping.reasoningMapping() != null) {
            return mapping.reasoningMapping();
        }
        var template = templates.get(mapping.template());
        return template != null ? template.defaultReasoningMapping() : null;
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
        result.setStructuredOutput(
            prefer(override.getStructuredOutput(), base.getStructuredOutput()));
        result.setToolStrict(prefer(override.getToolStrict(), base.getToolStrict()));
        result.setStore(prefer(override.getStore(), base.getStore()));
        result.setStreamUsage(prefer(override.getStreamUsage(), base.getStreamUsage()));
        result.setPromptCacheKey(prefer(override.getPromptCacheKey(), base.getPromptCacheKey()));
        result.setContextTokens(prefer(override.getContextTokens(), base.getContextTokens()));
        result.setInputTokens(prefer(override.getInputTokens(), base.getInputTokens()));
        result.setOutputTokens(prefer(override.getOutputTokens(), base.getOutputTokens()));
        result.setToolChoices(prefer(override.getToolChoices(), base.getToolChoices()));
        result.setServiceTiers(prefer(override.getServiceTiers(), base.getServiceTiers()));
        result.setReasoning(
            mergeReasoningConstraints(base.getReasoning(), override.getReasoning()));
        return result;
    }

    private ModelParameterConstraints.Reasoning mergeReasoningConstraints(
        ModelParameterConstraints.Reasoning base, ModelParameterConstraints.Reasoning override) {
        if (base == null && override == null) {
            return null;
        }
        var inherited = base != null ? base : new ModelParameterConstraints.Reasoning();
        var configured = override != null ? override : new ModelParameterConstraints.Reasoning();
        var result = new ModelParameterConstraints.Reasoning();
        result.setSupported(prefer(configured.getSupported(), inherited.getSupported()));
        result.setToggle(prefer(configured.getToggle(), inherited.getToggle()));
        result.setEfforts(prefer(configured.getEfforts(), inherited.getEfforts()));
        result.setBudgetMinimum(
            prefer(configured.getBudgetMinimum(), inherited.getBudgetMinimum()));
        result.setBudgetMaximum(
            prefer(configured.getBudgetMaximum(), inherited.getBudgetMaximum()));
        return result;
    }

    private <T> T prefer(T override, T inherited) {
        return override != null ? override : inherited;
    }

    private Map<ModelParameter, DefaultParameterMapping> defaultMappings(
        ModelResolution resolution, ModelType modelType) {
        var providerType = resolution.providerType();
        var adapterType = defaultMappingAdapter(resolution, modelType);
        if (adapterType == null) {
            return providerType.getDefaultParameterMappings();
        }
        return providerType.getDefaultParameterMappings(adapterType);
    }

    private AdapterType defaultMappingAdapter(
        ModelResolution resolution, ModelType modelType) {
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
        EnumMap<ModelParameter, EffectiveMapping> target,
        ModelParameterMappings mappings, EffectiveParameterMappings.Source source,
        ModelType modelType) {
        for (var entry : catalog.selections(mappings)) {
            if (entry.definition().modelType() != modelType
                || entry.selection().getMode() == null
                || entry.selection().getMode() == ModelParameterMappings.Mode.INHERIT) {
                continue;
            }
            var selection = entry.selection();
            target.put(entry.parameter(), new EffectiveMapping(
                selection.getMode(), selection.getTemplate(), selection.getField(),
                selection.getReasoningMapping(),
                source));
        }
    }

    private EffectiveMapping unsupported(
        EffectiveParameterMappings.Source source) {
        return new EffectiveMapping(
            ModelParameterMappings.Mode.UNSUPPORTED, null, null, null, source);
    }
}