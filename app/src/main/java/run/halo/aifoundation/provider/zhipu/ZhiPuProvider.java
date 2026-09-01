package run.halo.aifoundation.provider.zhipu;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** Dedicated BigModel integration for GLM Chat, embedding, rerank, and synchronous image APIs. */
@Component
public class ZhiPuProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";

    @Override
    public String getProviderType() {
        return "zhipuai";
    }

    @Override
    public String getDisplayName() {
        return "智谱开放平台";
    }

    @Override
    public String getDescription() {
        return "智谱原生 AI 服务，支持思考、流式工具、多模态、向量、重排序和图像生成。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/zhipu.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://bigmodel.cn/";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.bigmodel.cn/";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public boolean requiresBaseUrl() {
        return false;
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.ZHIPU_CHAT, AdapterType.ZHIPU_MESSAGES,
            AdapterType.ZHIPU_EMBEDDING,
            AdapterType.RERANK, AdapterType.ZHIPU_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.ALL;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case ZHIPU_CHAT -> ProviderFeatureSets.ALL;
            case ZHIPU_MESSAGES -> ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
        };
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.zhipu";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case ZHIPU_CHAT -> "reasoning.zhipu";
            case ZHIPU_MESSAGES -> "reasoning.messages-thinking";
            default -> null;
        };
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 64;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new ZhiPuChatModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case ZHIPU_CHAT -> new ZhiPuChatModel(options, webClientBuilder(provider));
            case ZHIPU_MESSAGES -> new ZhiPuMessagesModel(options.mutate()
                .baseUrl(ProviderUris.withoutTrailingPath(options.getBaseUrl(), "/api/paas/v4"))
                .build(), webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported Zhipu language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = ZhiPuEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .build();
        return new ZhiPuEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new ZhiPuRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new ZhiPuImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(
            ReasoningControlOptions.unsupported(), StructuredOutputSupport.JSON_OBJECT);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.ZHIPU_MESSAGES) {
            return languageModelProviderOptions(
                ReasoningControlOptions.unsupported(), StructuredOutputSupport.PROMPT_ONLY);
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        ReasoningControlOptions reasoning, StructuredOutputSupport structuredOutput) {
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, false, structuredOutput);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(false)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(structuredOutput)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(ZhiPuEmbeddingOptionsFactory::build);
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        for (var parameter : List.of(ModelParameter.PRESENCE_PENALTY,
            ModelParameter.FREQUENCY_PENALTY, ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.REPETITION_PENALTY, ModelParameter.SEED, ModelParameter.LOGPROBS,
            ModelParameter.TOP_LOGPROBS, ModelParameter.PARALLEL_TOOL_CALLS)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }

    private String reasoningContent(org.springframework.ai.chat.messages.AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        var value = message.getMetadata().get("reasoningContent");
        return value != null ? value.toString() : null;
    }

}
