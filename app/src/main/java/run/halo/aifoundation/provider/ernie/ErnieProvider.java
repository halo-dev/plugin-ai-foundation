package run.halo.aifoundation.provider.ernie;

import java.net.URI;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

@Component
public class ErnieProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://qianfan.baidubce.com/v2";

    @Override
    public String getProviderType() {
        return "ernie";
    }

    @Override
    public String getDisplayName() {
        return "百度千帆";
    }

    @Override
    public String getDescription() {
        return "百度千帆 v2 原生模型服务，支持 Responses、联网搜索、多模态向量、重排和图像。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/wenxin.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://cloud.baidu.com/product/wenxinworkshop";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://cloud.baidu.com/doc/qianfan-docs/index.html";
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
        return List.of(AdapterType.ERNIE_RESPONSES, AdapterType.ERNIE_CHAT,
            AdapterType.ERNIE_MESSAGES, AdapterType.ERNIE_EMBEDDING, AdapterType.RERANK,
            AdapterType.ERNIE_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        // The current v2 language APIs document image input but not an OpenAI-style audio input
        // contract. Keep audio out of the provider-wide feature ceiling.
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.thinking-type";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case ERNIE_RESPONSES -> "reasoning.responses-effort";
            case ERNIE_CHAT -> "reasoning.thinking-type";
            case ERNIE_MESSAGES -> "reasoning.messages-thinking";
            default -> null;
        };
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 16;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return responsesModel(provider, apiKey, modelId);
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case ERNIE_RESPONSES -> new ErnieResponsesModel(options, webClientBuilder(provider));
            case ERNIE_CHAT -> new ErnieChatModel(options, webClientBuilder(provider));
            case ERNIE_MESSAGES -> new ErnieMessagesModel(options.mutate()
                .baseUrl(ProviderUris.withoutTrailingPath(options.getBaseUrl(), "/v2"))
                .build(), webClientBuilder(provider));
            default -> throw new IllegalArgumentException("Unsupported Qianfan language adapter: "
                + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = ErnieEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .build();
        return new ErnieEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new ErnieRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new ErnieImageGenerationClient(new ImageGenerationClientOptions(getProviderType(),
            resolveBaseUrl(provider), apiKey, modelId, null), webClientBuilder(provider));
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(ErnieEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(StructuredOutputSupport.JSON_SCHEMA);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.ERNIE_MESSAGES) {
            return languageModelProviderOptions(StructuredOutputSupport.PROMPT_ONLY);
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        StructuredOutputSupport structuredOutput) {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, false, structuredOutput);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
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
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/models").build(), null)
            .map(root -> {
                var data = listValue(root, "data");
                if (data == null) {
                    return List.of();
                }
                return discoveredModelsFromNodes(data, "id", this::discoveredModel);
            });
    }

    private DiscoveredModel discoveredModel(Map<?, ?> node) {
        var modelId = stringValue(node, "id");
        var modelType = modelType(stringValue(node, "type"));
        if (modelId.isBlank() || modelType == null) {
            return null;
        }
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(modelType)
            .features(features(node, modelType))
            .adapterType(recommendAdapterType(modelType).orElse(null))
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .build();
    }

    private ModelType modelType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "chat" -> ModelType.LANGUAGE;
            case "embeddings", "embedding" -> ModelType.EMBEDDING;
            case "rerank", "reranker" -> ModelType.RERANK;
            case "text2image", "image2image" -> ModelType.IMAGE_GENERATION;
            // Video is intentionally not mislabeled as image generation: the public SDK does not
            // yet expose a video-generation model type.
            default -> null;
        };
    }

    private Set<ModelFeature> features(Map<?, ?> node, ModelType modelType) {
        if (modelType != ModelType.LANGUAGE) {
            return Set.of();
        }
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        var architecture = node.get("architecture") instanceof Map<?, ?> value ? value : Map.of();
        var modalities = architecture.get("input_modalities") instanceof Iterable<?> values
            ? values : List.of();
        for (var modality : modalities) {
            var value = modality != null ? modality.toString().toLowerCase(Locale.ROOT) : "";
            if (value.equals("image")) {
                features.add(ModelFeature.VISION);
            } else if (value.equals("audio")) {
                features.add(ModelFeature.AUDIO_INPUT);
            }
        }
        return Set.copyOf(features);
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.DIMENSIONS, DefaultParameterMapping.unsupported());
        defaults.put(ModelParameter.MIN_P, DefaultParameterMapping.unsupported());
        defaults.put(ModelParameter.IMAGE_SEED,
            DefaultParameterMapping.template("image.seed"));
        defaults.put(ModelParameter.NEGATIVE_PROMPT,
            DefaultParameterMapping.template("image.negative-prompt"));
        defaults.put(ModelParameter.RESPONSE_FORMAT, DefaultParameterMapping.unsupported());
        return Map.copyOf(defaults);
    }

    private ChatModel responsesModel(AiProvider provider, String apiKey, String modelId) {
        return new ErnieResponsesModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    private String reasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "reasoning_content", "reasoning")) {
            var value = message.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
