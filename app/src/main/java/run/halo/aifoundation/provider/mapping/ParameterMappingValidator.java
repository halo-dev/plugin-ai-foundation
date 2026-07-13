package run.halo.aifoundation.provider.mapping;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterMappings.Selection;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;

@Component
public class ParameterMappingValidator {

    private final ParameterMappingTemplateRegistry registry;

    public ParameterMappingValidator(ParameterMappingTemplateRegistry registry) {
        this.registry = registry;
    }

    public void normalize(ModelParameterMappings mappings) {
        entries(mappings).forEach(entry -> {
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
        for (var entry : entries(mappings)) {
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
        for (var entry : entries(mappings)) {
            validateSelection(entry, List.of(adapterType), modelType);
        }
    }

    private void validateSelection(Entry entry, List<AdapterType> adapters, ModelType modelType) {
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
        if (modelType != null && descriptor.parameter().getModelType() != modelType) {
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

    private void validateField(Entry entry, String field) {
        if (!StringUtils.hasText(field)) {
            return;
        }
        if (field.length() > 128
            || !field.matches("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*){0,3}")) {
            throw invalid(entry,
                "field must be a dotted identifier with at most four segments");
        }
    }

    private void validateConfiguration(Entry entry,
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

    private int validateReasoningValue(Entry entry,
        ParameterMappingTemplateDescriptor descriptor,
        ModelParameterMappings.ReasoningValueMapping value) {
        if (value == null) {
            return 0;
        }
        if (!StringUtils.hasText(value.getField()) || value.getValueType() == null
            || value.getValue() == null || value.getValue().isBlank()) {
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
        if (value.getValueType() == ModelParameterMappings.ValueType.BOOLEAN
            && !"true".equals(value.getValue()) && !"false".equals(value.getValue())) {
            throw invalid(entry, "Boolean reasoning values must be true or false");
        }
        return 1;
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
        if (!StringUtils.hasText(value.getField()) && value.getValueType() == null
            && !StringUtils.hasText(value.getValue())) {
            return null;
        }
        return value;
    }

    private void rejectIrrelevantDomains(ModelParameterMappings mappings, ModelType modelType) {
        if (modelType == null) {
            throw new IllegalArgumentException("modelType is required for parameter mappings");
        }
        if (mappings.getLanguage() != null && modelType != ModelType.LANGUAGE) {
            throw new IllegalArgumentException("language parameter mappings require language model");
        }
        if (mappings.getEmbedding() != null && modelType != ModelType.EMBEDDING) {
            throw new IllegalArgumentException("embedding parameter mappings require embedding model");
        }
        if (mappings.getRerank() != null && modelType != ModelType.RERANK) {
            throw new IllegalArgumentException("rerank parameter mappings require rerank model");
        }
        if (mappings.getImageGeneration() != null
            && modelType != ModelType.IMAGE_GENERATION) {
            throw new IllegalArgumentException(
                "imageGeneration parameter mappings require image-generation model");
        }
    }

    private IllegalArgumentException invalid(Entry entry, String detail) {
        return new IllegalArgumentException("Invalid parameter mapping " + entry.parameter().name()
            + ": " + detail);
    }

    static List<Entry> entries(ModelParameterMappings mappings) {
        var entries = new ArrayList<Entry>();
        if (mappings == null) {
            return entries;
        }
        var language = mappings.getLanguage();
        if (language != null) {
            add(entries, ModelParameter.MAX_OUTPUT_TOKENS, language.getMaxOutputTokens());
            add(entries, ModelParameter.TEMPERATURE, language.getTemperature());
            add(entries, ModelParameter.TOP_P, language.getTopP());
            add(entries, ModelParameter.TOP_K, language.getTopK());
            add(entries, ModelParameter.MIN_P, language.getMinP());
            add(entries, ModelParameter.PRESENCE_PENALTY, language.getPresencePenalty());
            add(entries, ModelParameter.FREQUENCY_PENALTY, language.getFrequencyPenalty());
            add(entries, ModelParameter.REPETITION_PENALTY, language.getRepetitionPenalty());
            add(entries, ModelParameter.STOP_SEQUENCES, language.getStopSequences());
            add(entries, ModelParameter.SEED, language.getSeed());
            add(entries, ModelParameter.LOGPROBS, language.getLogprobs());
            add(entries, ModelParameter.TOP_LOGPROBS, language.getTopLogprobs());
            add(entries, ModelParameter.PARALLEL_TOOL_CALLS, language.getParallelToolCalls());
            add(entries, ModelParameter.REASONING, language.getReasoning());
        }
        if (mappings.getEmbedding() != null) {
            add(entries, ModelParameter.DIMENSIONS, mappings.getEmbedding().getDimensions());
        }
        if (mappings.getRerank() != null) {
            add(entries, ModelParameter.TOP_N, mappings.getRerank().getTopN());
        }
        var image = mappings.getImageGeneration();
        if (image != null) {
            add(entries, ModelParameter.IMAGE_COUNT, image.getN());
            add(entries, ModelParameter.IMAGE_SIZE, image.getSize());
            add(entries, ModelParameter.ASPECT_RATIO, image.getAspectRatio());
            add(entries, ModelParameter.IMAGE_SEED, image.getSeed());
            add(entries, ModelParameter.RESPONSE_FORMAT, image.getResponseFormat());
            add(entries, ModelParameter.NEGATIVE_PROMPT, image.getNegativePrompt());
        }
        return List.copyOf(entries);
    }

    private static void add(List<Entry> entries, ModelParameter parameter, Selection selection) {
        if (selection != null) {
            entries.add(new Entry(parameter, selection));
        }
    }

    record Entry(ModelParameter parameter, Selection selection) {
    }
}
