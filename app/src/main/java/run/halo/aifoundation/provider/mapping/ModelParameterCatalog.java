package run.halo.aifoundation.provider.mapping;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.support.ModelParameterDomain;
import run.halo.aifoundation.provider.support.ModelType;

@Component
public final class ModelParameterCatalog {

    private static final List<ModelParameterDefinition> DEFINITIONS = createDefinitions();
    private static final Map<ModelParameter, ModelParameterDefinition> BY_PARAMETER = index();

    public List<ModelParameterDefinition> definitions() {
        return DEFINITIONS;
    }

    public ModelParameterDefinition definition(ModelParameter parameter) {
        var definition = BY_PARAMETER.get(parameter);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown model parameter: " + parameter);
        }
        return definition;
    }

    public List<ModelParameterDefinition> definitionsFor(Collection<ModelType> modelTypes) {
        if (modelTypes == null || modelTypes.isEmpty()) {
            return List.of();
        }
        return DEFINITIONS.stream()
            .filter(definition -> modelTypes.contains(definition.modelType()))
            .toList();
    }

    public List<ConfiguredSelection> selections(ModelParameterMappings mappings) {
        if (mappings == null) {
            return List.of();
        }
        return DEFINITIONS.stream()
            .map(definition -> new ConfiguredSelection(definition, definition.read(mappings)))
            .filter(entry -> entry.selection() != null)
            .toList();
    }

    public List<ModelParameterDomain> presentDomains(ModelParameterMappings mappings) {
        if (mappings == null) {
            return List.of();
        }
        return Arrays.stream(ModelParameterDomain.values())
            .filter(domain -> domain.isPresent(mappings))
            .toList();
    }

    public record ConfiguredSelection(ModelParameterDefinition definition,
                                      ModelParameterMappings.Selection selection) {
        public ModelParameter parameter() {
            return definition.parameter();
        }
    }

    private static Map<ModelParameter, ModelParameterDefinition> index() {
        var values = new EnumMap<ModelParameter, ModelParameterDefinition>(ModelParameter.class);
        for (var definition : DEFINITIONS) {
            var previous = values.put(definition.parameter(), definition);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate model parameter definition: " + definition.parameter());
            }
        }
        return Map.copyOf(values);
    }

    private static List<ModelParameterDefinition> createDefinitions() {
        return List.of(
            language(ModelParameter.MAX_OUTPUT_TOKENS, "maxOutputTokens", "最大输出 Token",
                "限制单次生成的最大输出长度", true,
                ModelParameterMappings.LanguageMappings::getMaxOutputTokens,
                ModelParameterMappings.LanguageMappings::setMaxOutputTokens),
            language(ModelParameter.TEMPERATURE, "temperature", "随机性（Temperature）",
                "控制生成结果的随机程度", true,
                ModelParameterMappings.LanguageMappings::getTemperature,
                ModelParameterMappings.LanguageMappings::setTemperature),
            language(ModelParameter.TOP_P, "topP", "Top P", "按累计概率限制候选 Token", true,
                ModelParameterMappings.LanguageMappings::getTopP,
                ModelParameterMappings.LanguageMappings::setTopP),
            language(ModelParameter.TOP_K, "topK", "Top K", "限制候选 Token 数量", false,
                ModelParameterMappings.LanguageMappings::getTopK,
                ModelParameterMappings.LanguageMappings::setTopK),
            language(ModelParameter.MIN_P, "minP", "Min P", "过滤低于相对概率阈值的 Token", false,
                ModelParameterMappings.LanguageMappings::getMinP,
                ModelParameterMappings.LanguageMappings::setMinP),
            language(ModelParameter.PRESENCE_PENALTY, "presencePenalty", "存在惩罚",
                "降低已出现内容再次出现的概率", false,
                ModelParameterMappings.LanguageMappings::getPresencePenalty,
                ModelParameterMappings.LanguageMappings::setPresencePenalty),
            language(ModelParameter.FREQUENCY_PENALTY, "frequencyPenalty", "频率惩罚",
                "按出现频率降低重复内容", false,
                ModelParameterMappings.LanguageMappings::getFrequencyPenalty,
                ModelParameterMappings.LanguageMappings::setFrequencyPenalty),
            language(ModelParameter.REPETITION_PENALTY, "repetitionPenalty", "重复惩罚",
                "控制重复 Token 的惩罚倍率", false,
                ModelParameterMappings.LanguageMappings::getRepetitionPenalty,
                ModelParameterMappings.LanguageMappings::setRepetitionPenalty),
            language(ModelParameter.STOP_SEQUENCES, "stopSequences", "停止序列",
                "遇到指定文本序列时停止生成", false,
                ModelParameterMappings.LanguageMappings::getStopSequences,
                ModelParameterMappings.LanguageMappings::setStopSequences),
            language(ModelParameter.SEED, "seed", "随机种子", "尽可能复现相同的生成结果", false,
                ModelParameterMappings.LanguageMappings::getSeed,
                ModelParameterMappings.LanguageMappings::setSeed),
            language(ModelParameter.LOGPROBS, "logprobs", "Token 概率",
                "返回输出 Token 的对数概率", false,
                ModelParameterMappings.LanguageMappings::getLogprobs,
                ModelParameterMappings.LanguageMappings::setLogprobs),
            language(ModelParameter.TOP_LOGPROBS, "topLogprobs", "候选 Token 概率数",
                "返回每个位置概率最高的候选 Token", false,
                ModelParameterMappings.LanguageMappings::getTopLogprobs,
                ModelParameterMappings.LanguageMappings::setTopLogprobs),
            language(ModelParameter.PARALLEL_TOOL_CALLS, "parallelToolCalls", "并行工具调用",
                "允许模型在一步中发起多个工具调用", false,
                ModelParameterMappings.LanguageMappings::getParallelToolCalls,
                ModelParameterMappings.LanguageMappings::setParallelToolCalls),
            language(ModelParameter.REASONING, "reasoning", "推理模式",
                "映射开启、关闭及低中高推理强度", true,
                ModelParameterMappings.LanguageMappings::getReasoning,
                ModelParameterMappings.LanguageMappings::setReasoning),
            embedding(ModelParameter.DIMENSIONS, "dimensions", "向量维度",
                "指定 Embedding 输出向量的维度", true,
                ModelParameterMappings.EmbeddingMappings::getDimensions,
                ModelParameterMappings.EmbeddingMappings::setDimensions),
            rerank(ModelParameter.TOP_N, "topN", "返回结果数",
                "指定 Rerank 返回的最高排名结果数", true,
                ModelParameterMappings.RerankMappings::getTopN,
                ModelParameterMappings.RerankMappings::setTopN),
            image(ModelParameter.IMAGE_COUNT, "n", "图片数量", "指定单次请求生成的图片数量", true,
                ModelParameterMappings.ImageGenerationMappings::getN,
                ModelParameterMappings.ImageGenerationMappings::setN),
            image(ModelParameter.IMAGE_SIZE, "size", "图片尺寸", "指定图片宽高或尺寸字符串", true,
                ModelParameterMappings.ImageGenerationMappings::getSize,
                ModelParameterMappings.ImageGenerationMappings::setSize),
            image(ModelParameter.ASPECT_RATIO, "aspectRatio", "图片比例", "指定图片宽高比", true,
                ModelParameterMappings.ImageGenerationMappings::getAspectRatio,
                ModelParameterMappings.ImageGenerationMappings::setAspectRatio),
            image(ModelParameter.IMAGE_SEED, "seed", "图片随机种子", "尽可能复现相同的图片结果", false,
                ModelParameterMappings.ImageGenerationMappings::getSeed,
                ModelParameterMappings.ImageGenerationMappings::setSeed),
            image(ModelParameter.RESPONSE_FORMAT, "responseFormat", "图片返回格式",
                "选择 URL 或 Base64 等返回格式", false,
                ModelParameterMappings.ImageGenerationMappings::getResponseFormat,
                ModelParameterMappings.ImageGenerationMappings::setResponseFormat),
            image(ModelParameter.NEGATIVE_PROMPT, "negativePrompt", "反向提示词",
                "描述图片中不希望出现的内容", false,
                ModelParameterMappings.ImageGenerationMappings::getNegativePrompt,
                ModelParameterMappings.ImageGenerationMappings::setNegativePrompt)
        );
    }

    private static ModelParameterDefinition language(ModelParameter parameter, String field,
        String displayName, String description, boolean common,
        Function<ModelParameterMappings.LanguageMappings, ModelParameterMappings.Selection> reader,
        BiConsumer<ModelParameterMappings.LanguageMappings, ModelParameterMappings.Selection> writer) {
        return definition(parameter, ModelParameterDomain.LANGUAGE, field, displayName,
            description, common, ModelParameterMappings::getLanguage,
            ModelParameterMappings::setLanguage, ModelParameterMappings.LanguageMappings::new,
            reader, writer);
    }

    private static ModelParameterDefinition embedding(ModelParameter parameter, String field,
        String displayName, String description, boolean common,
        Function<ModelParameterMappings.EmbeddingMappings, ModelParameterMappings.Selection> reader,
        BiConsumer<ModelParameterMappings.EmbeddingMappings, ModelParameterMappings.Selection> writer) {
        return definition(parameter, ModelParameterDomain.EMBEDDING, field, displayName,
            description, common, ModelParameterMappings::getEmbedding,
            ModelParameterMappings::setEmbedding, ModelParameterMappings.EmbeddingMappings::new,
            reader, writer);
    }

    private static ModelParameterDefinition rerank(ModelParameter parameter, String field,
        String displayName, String description, boolean common,
        Function<ModelParameterMappings.RerankMappings, ModelParameterMappings.Selection> reader,
        BiConsumer<ModelParameterMappings.RerankMappings, ModelParameterMappings.Selection> writer) {
        return definition(parameter, ModelParameterDomain.RERANK, field, displayName,
            description, common, ModelParameterMappings::getRerank,
            ModelParameterMappings::setRerank, ModelParameterMappings.RerankMappings::new,
            reader, writer);
    }

    private static ModelParameterDefinition image(ModelParameter parameter, String field,
        String displayName, String description, boolean common,
        Function<ModelParameterMappings.ImageGenerationMappings, ModelParameterMappings.Selection> reader,
        BiConsumer<ModelParameterMappings.ImageGenerationMappings, ModelParameterMappings.Selection> writer) {
        return definition(parameter, ModelParameterDomain.IMAGE_GENERATION, field,
            displayName, description, common, ModelParameterMappings::getImageGeneration,
            ModelParameterMappings::setImageGeneration,
            ModelParameterMappings.ImageGenerationMappings::new, reader, writer);
    }

    private static <D> ModelParameterDefinition definition(ModelParameter parameter,
        ModelParameterDomain domain, String field, String displayName, String description,
        boolean common, Function<ModelParameterMappings, D> domainReader,
        BiConsumer<ModelParameterMappings, D> domainWriter, Supplier<D> domainFactory,
        Function<D, ModelParameterMappings.Selection> selectionReader,
        BiConsumer<D, ModelParameterMappings.Selection> selectionWriter) {
        return new ModelParameterDefinition(parameter, domain, field, displayName,
            description, common, new ModelParameterDefinition.SelectionAccessor() {
                @Override
                public ModelParameterMappings.Selection read(ModelParameterMappings mappings) {
                    var domainMappings = domainReader.apply(mappings);
                    return domainMappings == null ? null : selectionReader.apply(domainMappings);
                }

                @Override
                public void write(ModelParameterMappings mappings,
                    ModelParameterMappings.Selection selection) {
                    var domainMappings = domainReader.apply(mappings);
                    if (domainMappings == null) {
                        domainMappings = domainFactory.get();
                        domainWriter.accept(mappings, domainMappings);
                    }
                    selectionWriter.accept(domainMappings, selection);
                }
            });
    }

}
