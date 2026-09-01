package run.halo.aifoundation.provider.siliconflow;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
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
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** SiliconFlow integration backed by its provider-owned Chat and native domain APIs. */
@Component
public class SiliconFlowProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";
    @Override
    public String getProviderType() {
        return "siliconflow";
    }

    @Override
    public String getDisplayName() {
        return "硅基流动";
    }

    @Override
    public String getDescription() {
        return "硅基流动原生模型平台，支持独立对话、向量、重排序和图像能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/siliconcloud.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.siliconflow.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.siliconflow.com";
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
        return List.of(AdapterType.SILICONFLOW_CHAT, AdapterType.SILICONFLOW_MESSAGES,
            AdapterType.SILICONFLOW_EMBEDDING, AdapterType.RERANK,
            AdapterType.SILICONFLOW_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.ALL;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.enable-thinking";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        if (adapterType == AdapterType.SILICONFLOW_CHAT) {
            return "reasoning.enable-thinking";
        }
        return null;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case SILICONFLOW_CHAT -> ProviderFeatureSets.ALL;
            case SILICONFLOW_MESSAGES -> ProviderFeatureSets.TEXT;
            default -> List.of();
        };
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 32;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new SiliconFlowChatModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case SILICONFLOW_CHAT -> new SiliconFlowChatModel(options,
                webClientBuilder(provider));
            case SILICONFLOW_MESSAGES -> new SiliconFlowMessagesModel(options,
                webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported SiliconFlow language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = SiliconFlowEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .build();
        return new SiliconFlowEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new SiliconFlowRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new SiliconFlowImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(StructuredOutputSupport.JSON_OBJECT, true);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.SILICONFLOW_MESSAGES) {
            return languageModelProviderOptions(StructuredOutputSupport.PROMPT_ONLY, false);
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        StructuredOutputSupport structuredOutput, boolean nativeStrictToolSchemas) {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, nativeStrictToolSchemas, structuredOutput);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
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
        return new EmbeddingModelProviderOptions(SiliconFlowEmbeddingOptionsFactory::build);
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return Mono.zip(
            discoverTypedModels(provider, apiKey, "chat", ModelType.LANGUAGE,
                AdapterType.SILICONFLOW_CHAT, Set.of(ModelFeature.STREAMING)),
            discoverTypedModels(provider, apiKey, "embedding", ModelType.EMBEDDING,
                AdapterType.SILICONFLOW_EMBEDDING, Set.of()),
            discoverTypedModels(provider, apiKey, "reranker", ModelType.RERANK,
                AdapterType.RERANK, Set.of()),
            discoverImageIds(provider, apiKey, "text-to-image"),
            discoverImageIds(provider, apiKey, "image-to-image")
        ).map(tuple -> combine(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(),
            tuple.getT5()));
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        for (var parameter : List.of(ModelParameter.PRESENCE_PENALTY,
            ModelParameter.REPETITION_PENALTY, ModelParameter.SEED, ModelParameter.LOGPROBS,
            ModelParameter.TOP_LOGPROBS, ModelParameter.PARALLEL_TOOL_CALLS)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }

    private Mono<List<DiscoveredModel>> discoverTypedModels(AiProvider provider, String apiKey,
        String subType, ModelType modelType, AdapterType adapterType, Set<ModelFeature> features) {
        return discoveryData(provider, apiKey, subType)
            .map(nodes -> discoveredModelsFromNodes(nodes, "id",
                node -> remoteDiscoveredModel(stringValue(node, "id"), modelType, features,
                    adapterType)));
    }

    private Mono<Set<String>> discoverImageIds(AiProvider provider, String apiKey,
        String subType) {
        return discoveryData(provider, apiKey, subType)
            .map(nodes -> nodes.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(node -> stringValue(node, "id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private Mono<List<?>> discoveryData(AiProvider provider, String apiKey, String subType) {
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/models").queryParam("sub_type", subType).build(),
            this::customizeDiscoveryRequest)
            .map(root -> {
                var data = listValue(root, "data");
                return data != null ? data : List.of();
            });
    }

    private List<DiscoveredModel> combine(List<DiscoveredModel> chat,
        List<DiscoveredModel> embeddings, List<DiscoveredModel> rerank, Set<String> textToImage,
        Set<String> imageToImage) {
        var models = new LinkedHashMap<String, DiscoveredModel>();
        chat.forEach(model -> models.put(key(model), model));
        embeddings.forEach(model -> models.put(key(model), model));
        rerank.forEach(model -> models.put(key(model), model));
        var imageIds = new java.util.TreeSet<String>();
        imageIds.addAll(textToImage);
        imageIds.addAll(imageToImage);
        imageIds.forEach(id -> {
            var model = imageModel(id, textToImage.contains(id), imageToImage.contains(id));
            models.put(key(model), model);
        });
        return List.copyOf(models.values());
    }

    private String key(DiscoveredModel model) {
        return model.modelType() + ":" + model.modelId();
    }

    private DiscoveredModel imageModel(String modelId, boolean textToImage,
        boolean imageToImage) {
        var capability = ImageGenerationCapability.builder()
            .textToImage(textToImage)
            .imageToImage(imageToImage)
            .build();
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(ModelType.IMAGE_GENERATION)
            .adapterType(AdapterType.SILICONFLOW_IMAGE)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .capabilities(ModelCapabilities.builder().imageGeneration(capability).build())
            .capabilitySources(ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.REMOTE)
                .build())
            .build();
    }

    private String reasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "reasoning_content")) {
            var value = message.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
