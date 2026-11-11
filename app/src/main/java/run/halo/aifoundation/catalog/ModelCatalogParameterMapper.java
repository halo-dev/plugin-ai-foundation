package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterMappings.Mode;
import run.halo.aifoundation.extension.ModelParameterMappings.ReasoningMapping;
import run.halo.aifoundation.extension.ModelParameterMappings.ReasoningValueMapping;
import run.halo.aifoundation.extension.ModelParameterMappings.Selection;
import run.halo.aifoundation.extension.ModelParameterMappings.ValueType;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateDescriptor;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.support.AdapterProtocol;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

/** Combines model facts with reviewed adapter wire mappings, never with catalog SDK code. */
final class ModelCatalogParameterMapper {
    private static final ParameterMappingTemplateRegistry TEMPLATES = new ParameterMappingTemplateRegistry();
    private static final List<String> EFFORT_ORDER = List.of("minimal", "low", "medium", "high", "xhigh", "max");

    private ModelCatalogParameterMapper() {}

    static ModelParameterProfile map(
            AiProvider provider,
            AiProviderType type,
            DiscoveredModel model,
            ModelsDevCatalog.Entry entry,
            AdapterType adapter) {
        if (adapter == null
                || adapter.getModelType() != model.modelType()
                || !entry.matchesModelType(model.modelType())) {
            return null;
        }
        var protocol = ModelsDevProviderIdentity.protocol(adapter);
        if (protocol == null) {
            return null;
        }
        var api = entry.api(protocol);
        if (api.isMissingNode()) {
            return null;
        }
        var constraints = tokenConstraints(entry.model(), model.modelType());
        var mappings = new ModelParameterMappings();
        var result = profile(provider, type, model, adapter, mappings, constraints);
        if (model.modelType() == ModelType.LANGUAGE) {
            addLanguageConstraints(constraints, type, model, entry, adapter, api);
            mappings.setLanguage(languageMappings(type, adapter, entry.model(), api, constraints));
            result.setFeatureEvidence(providerEvidence(model));
        }
        return result;
    }

    private static ModelParameterConstraints tokenConstraints(JsonNode model, ModelType modelType) {
        var limits = model.path("limit");
        var constraints = new ModelParameterConstraints();
        constraints.setContextTokens(positive(limits.path("context")));
        constraints.setInputTokens(positive(limits.path("input")));
        // Non-chat output limits do not describe portable token limits, embedding dimensions,
        // image counts or reranking result counts.
        if (modelType == ModelType.LANGUAGE) {
            constraints.setOutputTokens(positive(limits.path("output")));
        }
        return constraints;
    }

    private static void addLanguageConstraints(
            ModelParameterConstraints constraints,
            AiProviderType type,
            DiscoveredModel model,
            ModelsDevCatalog.Entry entry,
            AdapterType adapter,
            JsonNode api) {
        var capabilities = entry.model().path("capabilities");
        var protocolCapabilities = api.path("capabilities");
        constraints.setTemperature(bool(capabilities, "temperature"));
        constraints.setTopP(bool(capabilities, "top_p"));
        constraints.setTopK(bool(capabilities, "top_k"));
        constraints.setToolCalling(supported(
                type, adapter, ModelFeature.TOOL_CALL, evidence(model, ModelFeature.TOOL_CALL, entry.tools())));
        constraints.setStructuredOutput(supported(
                type,
                adapter,
                ModelFeature.STRUCTURED_OUTPUT,
                evidence(model, ModelFeature.STRUCTURED_OUTPUT, entry.structuredOutput())));
        var tools = protocolCapabilities.path("tools");
        constraints.setToolStrict(bool(tools, "strict"));
        constraints.setToolChoices(strings(tools.path("choice")));
        constraints.setStore(flag(capabilities, protocolCapabilities, "store"));
        constraints.setStreamUsage(flag(capabilities, protocolCapabilities, "stream_usage"));
        constraints.setPromptCacheKey(flag(capabilities, protocolCapabilities, "prompt_cache_key"));
        constraints.setServiceTiers(strings(
                protocolCapabilities.has("service_tier")
                        ? protocolCapabilities.path("service_tier")
                        : capabilities.path("service_tier")));

        var reasoning = capabilities.path("reasoning");
        var reasoningSupported = supported(
                type, adapter, ModelFeature.REASONING, evidence(model, ModelFeature.REASONING, entry.reasoning()));
        if (reasoningSupported != null || reasoning.isObject()) {
            var controls = new ModelParameterConstraints.Reasoning();
            controls.setSupported(reasoningSupported);
            controls.setToggle(bool(reasoning, "toggle"));
            controls.setEfforts(strings(reasoning.path("effort")));
            controls.setBudgetMinimum(positive(reasoning.path("budget").path("min")));
            controls.setBudgetMaximum(positive(reasoning.path("budget").path("max")));
            constraints.setReasoning(controls);
        }
    }

    private static ModelParameterMappings.LanguageMappings languageMappings(
            AiProviderType type,
            AdapterType adapter,
            JsonNode model,
            JsonNode api,
            ModelParameterConstraints constraints) {
        var language = new ModelParameterMappings.LanguageMappings();
        language.setTemperature(sampling(constraints.getTemperature()));
        language.setTopP(sampling(constraints.getTopP()));
        language.setTopK(sampling(constraints.getTopK()));
        language.setMaxOutputTokens(outputTokenMapping(type, adapter, api));
        var reasoning = model.path("capabilities").path("reasoning");
        var controls = constraints.getReasoning();
        if (controls != null
                && (controls.getSupported() != null
                        || reasoning.has("toggle")
                        || reasoning.has("effort")
                        || reasoning.has("budget"))) {
            language.setReasoning(reasoning(type, adapter, reasoning, controls));
        }
        return language;
    }

    private static Selection outputTokenMapping(AiProviderType type, AdapterType adapter, JsonNode api) {
        // Unlike optional capability flags, v2 explicitly defines this omitted field's meaning.
        var tokenField = api.path("compatibility").path("max_tokens_field").asText("max_completion_tokens");
        if (adapter.getProtocol() != AdapterProtocol.CHAT_COMPLETIONS
                || !List.of("max_tokens", "max_completion_tokens").contains(tokenField)) {
            return null;
        }
        var template = template(type, adapter, ModelParameter.MAX_OUTPUT_TOKENS);
        if (template == null
                || !List.of("openai.max-tokens", "openai.max-completion-tokens").contains(template.id())) {
            return null;
        }
        var selection = selection(template.id());
        selection.setField(tokenField);
        return selection;
    }

    private static ModelParameterProfile profile(
            AiProvider provider,
            AiProviderType type,
            DiscoveredModel model,
            AdapterType adapter,
            ModelParameterMappings mappings,
            ModelParameterConstraints constraints) {
        var result = new ModelParameterProfile();
        result.setProviderType(provider.getSpec().getProviderType());
        var baseUrl = provider.getSpec().getBaseUrl();
        result.setProviderBaseUrl(baseUrl == null || baseUrl.isBlank() ? type.getDefaultBaseUrl() : baseUrl);
        result.setModelId(model.modelId());
        result.setAdapterType(adapter);
        result.setMappings(mappings);
        result.setConstraints(constraints);
        return result;
    }

    private static Selection reasoning(
            AiProviderType type, AdapterType adapter, JsonNode node, ModelParameterConstraints.Reasoning controls) {
        if (Boolean.FALSE.equals(controls.getSupported())) {
            return unsupported();
        }
        var template = template(type, adapter, ModelParameter.REASONING);
        // Kimi's toggle-only models use thinking.type; its effort models keep reasoning_effort.
        // Select the reviewed wire shape from model controls rather than model-name heuristics.
        if (adapter == AdapterType.KIMI_CHAT
                && Boolean.TRUE.equals(controls.getToggle())
                && (controls.getEfforts() == null || controls.getEfforts().isEmpty())) {
            template = TEMPLATES.get("reasoning.thinking-type");
        }
        if (template == null || template.defaultReasoningMapping() == null) {
            return unsupported();
        }
        var defaults = template.defaultReasoningMapping();
        var mapping = new ReasoningMapping();
        if (Boolean.TRUE.equals(controls.getToggle())) {
            mapping.setEnabled(supportedToggle(defaults.getEnabled(), controls.getEfforts()));
            mapping.setDisabled(supportedToggle(defaults.getDisabled(), controls.getEfforts()));
        }
        applyEffortMapping(mapping, adapter, defaults, controls.getEfforts());
        // Budgets are opt-in per adapter: v2 describes amounts, not native request paths.
        if (mapping.getLow() == null
                && node.path("budget").isObject()
                && (adapter == AdapterType.DASHSCOPE_CHAT || adapter == AdapterType.SILICONFLOW_CHAT)) {
            applyBudgetMapping(mapping, controls);
        }
        if (mapping.getEnabled() == null
                && mapping.getDisabled() == null
                && mapping.getLow() == null
                && mapping.getMedium() == null
                && mapping.getHigh() == null) {
            // Explicit model facts must not revive an unrelated provider-wide effort control.
            return unsupported();
        }
        var selection = selection(template.id());
        selection.setReasoningMapping(mapping);
        return selection;
    }

    private static ReasoningValueMapping supportedToggle(ReasoningValueMapping value, List<String> efforts) {
        // Effort-based toggle values are valid only when the catalog enumerates them.
        if (value == null || (isEffort(value) && (efforts == null || !efforts.contains(value.getValue())))) {
            return null;
        }
        return copy(value);
    }

    private static void applyEffortMapping(
            ReasoningMapping mapping, AdapterType adapter, ReasoningMapping defaults, List<String> efforts) {
        if (efforts == null) {
            return;
        }
        var ordered = EFFORT_ORDER.stream().filter(efforts::contains).toList();
        var effort = effortMapping(adapter, defaults);
        if (ordered.isEmpty() || effort == null) {
            return;
        }
        var low = ordered.contains("low") ? "low" : ordered.getFirst();
        var medium = ordered.contains("medium") ? "medium" : ordered.get(ordered.size() / 2);
        var high = ordered.getLast();
        mapping.setLow(withEnable(value(effort.getField(), ValueType.STRING, low), mapping));
        mapping.setMedium(withEnable(value(effort.getField(), ValueType.STRING, medium), mapping));
        mapping.setHigh(withEnable(value(effort.getField(), ValueType.STRING, high), mapping));
        if (mapping.getEnabled() == null) {
            mapping.setEnabled(copy(mapping.getMedium()));
        }
        if (efforts.contains("none")) {
            mapping.setDisabled(value(effort.getField(), ValueType.STRING, "none"));
        }
    }

    private static void applyBudgetMapping(ReasoningMapping mapping, ModelParameterConstraints.Reasoning controls) {
        mapping.setLow(budget(1024, controls, mapping));
        mapping.setMedium(budget(4096, controls, mapping));
        mapping.setHigh(budget(8192, controls, mapping));
        if (mapping.getEnabled() == null) {
            mapping.setEnabled(copy(mapping.getMedium()));
        }
    }

    private static ReasoningValueMapping effortMapping(AdapterType adapter, ReasoningMapping defaults) {
        var effort = defaults.getLow();
        if (effort != null && isEffort(effort)) {
            return effort;
        }
        // These adapters also accept reasoning_effort, although their provider defaults
        // expose only a toggle. The field comes from a reviewed template, never catalog text.
        return switch (adapter) {
            case DOUBAO_CHAT, SILICONFLOW_CHAT ->
                TEMPLATES.get("reasoning.effort").defaultReasoningMapping().getLow();
            default -> null;
        };
    }

    private static ReasoningValueMapping budget(
            long preset, ModelParameterConstraints.Reasoning controls, ReasoningMapping mapping) {
        var amount = Math.max(preset, controls.getBudgetMinimum() == null ? 1 : controls.getBudgetMinimum());
        if (controls.getBudgetMaximum() != null) {
            amount = Math.min(amount, controls.getBudgetMaximum());
        }
        if (amount > Integer.MAX_VALUE) {
            return null;
        }
        return withEnable(value("thinking_budget", ValueType.INTEGER, Long.toString(amount)), mapping);
    }

    private static ReasoningValueMapping withEnable(ReasoningValueMapping value, ReasoningMapping mapping) {
        var enabled = mapping.getEnabled();
        if (enabled != null && !enabled.getField().equals(value.getField())) {
            value.setAdditionalValues(List.of(copy(enabled)));
        }
        return value;
    }

    private static boolean isEffort(ReasoningValueMapping value) {
        return value.getValueType() == ValueType.STRING
                && (value.getField().endsWith("effort") || "think".equals(value.getField()));
    }

    private static ReasoningValueMapping copy(ReasoningValueMapping value) {
        if (value == null) {
            return null;
        }
        var copy = value(value.getField(), value.getValueType(), value.getValue());
        if (value.getAdditionalValues() != null) {
            copy.setAdditionalValues(value.getAdditionalValues().stream()
                    .map(ModelCatalogParameterMapper::copy)
                    .toList());
        }
        return copy;
    }

    private static ReasoningValueMapping value(String field, ValueType type, String text) {
        var value = new ReasoningValueMapping();
        value.setField(field);
        value.setValueType(type);
        value.setValue(text);
        return value;
    }

    private static ParameterMappingTemplateDescriptor template(
            AiProviderType type, AdapterType adapter, ModelParameter parameter) {
        var defaults = type.getDefaultParameterMappings(adapter);
        var mapping = defaults == null ? null : defaults.get(parameter);
        var descriptor = mapping == null ? null : TEMPLATES.get(mapping.template());
        return descriptor != null && descriptor.adapterTypes().contains(adapter) ? descriptor : null;
    }

    private static Selection sampling(Boolean supported) {
        return Boolean.FALSE.equals(supported) ? unsupported() : null;
    }

    private static Selection unsupported() {
        var selection = new Selection();
        selection.setMode(Mode.UNSUPPORTED);
        return selection;
    }

    private static Selection selection(String template) {
        var selection = new Selection();
        selection.setMode(Mode.TEMPLATE);
        selection.setTemplate(template);
        return selection;
    }

    private static Boolean evidence(DiscoveredModel model, ModelFeature feature, Boolean fallback) {
        if (model.featureEvidence().containsKey(feature)) {
            return model.featureEvidence().get(feature);
        }
        return model.source() == DiscoverySource.REMOTE && model.features().contains(feature) ? Boolean.TRUE : fallback;
    }

    private static Map<ModelFeature, Boolean> providerEvidence(DiscoveredModel model) {
        var evidence = new EnumMap<ModelFeature, Boolean>(ModelFeature.class);
        for (var feature : List.of(ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING)) {
            var value = evidence(model, feature, null);
            if (value != null) {
                evidence.put(feature, value);
            }
        }
        return Map.copyOf(evidence);
    }

    private static Boolean supported(
            AiProviderType type, AdapterType adapter, ModelFeature feature, Boolean supported) {
        return Boolean.TRUE.equals(supported)
                        && !type.getSupportedFeatures(adapter).contains(feature)
                ? Boolean.FALSE
                : supported;
    }

    private static Boolean bool(JsonNode node, String key) {
        return node.path(key).isBoolean() ? node.path(key).booleanValue() : null;
    }

    private static Boolean flag(JsonNode common, JsonNode protocol, String key) {
        return protocol.has(key) ? bool(protocol, key) : bool(common, key);
    }

    private static Long positive(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() > 0 ? node.longValue() : null;
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        var result = new ArrayList<String>();
        node.forEach(value -> {
            if (value.isTextual() && !value.textValue().isBlank()) {
                result.add(value.textValue());
            }
        });
        return List.copyOf(result);
    }
}
