package run.halo.aifoundation.provider.mapping;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.provider.support.AdapterType;

public final class ProviderParameterMappingDefaults {

    private ProviderParameterMappingDefaults() {
    }

    public static Map<ModelParameter, DefaultParameterMapping> forAdapters(
        List<AdapterType> adapters) {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        if (adapters == null) {
            return Map.of();
        }
        var modelTypes = adapters.stream().map(AdapterType::getModelType).distinct().toList();
        for (var parameter : ModelParameter.values()) {
            if (modelTypes.contains(parameter.getModelType())) {
                defaults.put(parameter, DefaultParameterMapping.unsupported());
            }
        }
        if (adapters.contains(AdapterType.OPENAI_CHAT)) {
            putLanguage(defaults, "openai.max-tokens");
        }
        if (adapters.contains(AdapterType.OLLAMA_CHAT)) {
            putOllamaLanguage(defaults);
            defaults.put(ModelParameter.REASONING,
                DefaultParameterMapping.template("reasoning.ollama-think"));
        }
        if (adapters.contains(AdapterType.OPENAI_EMBEDDING)) {
            defaults.put(ModelParameter.DIMENSIONS,
                DefaultParameterMapping.template("embedding.dimensions"));
        }
        if (adapters.contains(AdapterType.RERANK)) {
            defaults.put(ModelParameter.TOP_N, DefaultParameterMapping.template("rerank.top-n"));
        }
        adapters.stream().filter(ProviderParameterMappingDefaults::isImage).findFirst()
            .ifPresent(adapter -> putImage(defaults, adapter));
        return Map.copyOf(defaults);
    }

    private static void putLanguage(Map<ModelParameter, DefaultParameterMapping> defaults,
        String maxOutputTokens) {
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS, DefaultParameterMapping.template(maxOutputTokens));
        put(defaults, ModelParameter.TEMPERATURE, "chat.temperature");
        put(defaults, ModelParameter.TOP_P, "chat.top-p");
        put(defaults, ModelParameter.TOP_K, "chat.top-k");
        put(defaults, ModelParameter.MIN_P, "chat.min-p");
        put(defaults, ModelParameter.PRESENCE_PENALTY, "chat.presence-penalty");
        put(defaults, ModelParameter.FREQUENCY_PENALTY, "chat.frequency-penalty");
        put(defaults, ModelParameter.REPETITION_PENALTY, "chat.repetition-penalty");
        put(defaults, ModelParameter.STOP_SEQUENCES, "chat.stop");
        put(defaults, ModelParameter.SEED, "chat.seed");
        put(defaults, ModelParameter.LOGPROBS, "chat.logprobs");
        put(defaults, ModelParameter.TOP_LOGPROBS, "chat.top-logprobs");
        put(defaults, ModelParameter.PARALLEL_TOOL_CALLS, "chat.parallel-tool-calls");
    }

    private static void putOllamaLanguage(
        Map<ModelParameter, DefaultParameterMapping> defaults) {
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("ollama.num-predict"));
        put(defaults, ModelParameter.TEMPERATURE, "chat.temperature");
        put(defaults, ModelParameter.TOP_P, "chat.top-p");
        put(defaults, ModelParameter.TOP_K, "chat.top-k");
        put(defaults, ModelParameter.MIN_P, "chat.min-p");
        put(defaults, ModelParameter.PRESENCE_PENALTY, "chat.presence-penalty");
        put(defaults, ModelParameter.FREQUENCY_PENALTY, "chat.frequency-penalty");
        put(defaults, ModelParameter.REPETITION_PENALTY, "chat.repetition-penalty");
        put(defaults, ModelParameter.STOP_SEQUENCES, "chat.stop");
        put(defaults, ModelParameter.SEED, "chat.seed");
    }

    private static void put(Map<ModelParameter, DefaultParameterMapping> defaults,
        ModelParameter parameter, String template) {
        defaults.put(parameter, DefaultParameterMapping.template(template));
    }

    private static void putImage(Map<ModelParameter, DefaultParameterMapping> defaults,
        AdapterType adapter) {
        switch (adapter) {
            case OPENAI_IMAGE -> {
                put(defaults, ModelParameter.IMAGE_COUNT, "image.n");
                put(defaults, ModelParameter.IMAGE_SIZE, "image.size");
                put(defaults, ModelParameter.RESPONSE_FORMAT, "image.response-format.openai");
            }
            case OPENROUTER_IMAGE -> {
                put(defaults, ModelParameter.IMAGE_COUNT, "image.n");
                put(defaults, ModelParameter.IMAGE_SIZE, "image.size");
                put(defaults, ModelParameter.ASPECT_RATIO, "image.aspect-ratio");
                put(defaults, ModelParameter.IMAGE_SEED, "image.seed");
            }
            case DASHSCOPE_IMAGE -> {
                put(defaults, ModelParameter.IMAGE_COUNT, "image.parameters.n");
                put(defaults, ModelParameter.IMAGE_SIZE, "image.parameters.size");
                put(defaults, ModelParameter.IMAGE_SEED, "image.parameters.seed");
                put(defaults, ModelParameter.NEGATIVE_PROMPT,
                    "image.parameters.negative-prompt");
            }
            case DOUBAO_IMAGE -> {
                put(defaults, ModelParameter.IMAGE_SIZE, "image.size");
                put(defaults, ModelParameter.IMAGE_SEED, "image.seed");
                put(defaults, ModelParameter.RESPONSE_FORMAT, "image.response-format.openai");
            }
            case MINIMAX_IMAGE -> {
                put(defaults, ModelParameter.IMAGE_COUNT, "image.n");
                put(defaults, ModelParameter.IMAGE_SIZE, "image.minimax.dimensions");
                put(defaults, ModelParameter.ASPECT_RATIO, "image.aspect-ratio");
                put(defaults, ModelParameter.IMAGE_SEED, "image.seed");
                put(defaults, ModelParameter.RESPONSE_FORMAT, "image.response-format.minimax");
                put(defaults, ModelParameter.NEGATIVE_PROMPT, "image.negative-prompt");
            }
            case SILICONFLOW_IMAGE -> {
                put(defaults, ModelParameter.IMAGE_COUNT, "image.siliconflow.batch-size");
                put(defaults, ModelParameter.IMAGE_SIZE, "image.siliconflow.image-size");
                put(defaults, ModelParameter.IMAGE_SEED, "image.seed");
                put(defaults, ModelParameter.NEGATIVE_PROMPT, "image.negative-prompt");
            }
            default -> { }
        }
    }

    private static boolean isImage(AdapterType adapter) {
        return adapter.getModelType() == run.halo.aifoundation.provider.support.ModelType.IMAGE_GENERATION;
    }
}
