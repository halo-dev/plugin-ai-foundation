package run.halo.aifoundation.provider.aihubmix;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** AIHubMix gateway integration with provider-owned protocol and domain adapters. */
@Component
public class AiHubMixProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://aihubmix.com/v1";
    private static final String MODEL_CATALOG_PATH = "/api/v1/models";

    @Override
    public String getProviderType() {
        return "aihubmix";
    }

    @Override
    public String getDisplayName() {
        return "AIHubMix";
    }

    @Override
    public String getDescription() {
        return "AIHubMix 原生网关适配，支持 Responses、Chat、嵌入、重排与模型路由图片预测。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/aihubmix.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://aihubmix.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.aihubmix.com";
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
        return List.of(AdapterType.AIHUBMIX_RESPONSES, AdapterType.AIHUBMIX_CHAT,
            AdapterType.AIHUBMIX_MESSAGES, AdapterType.AIHUBMIX_EMBEDDING,
            AdapterType.RERANK, AdapterType.AIHUBMIX_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.ALL;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.responses-effort";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        if (adapterType == AdapterType.AIHUBMIX_CHAT) {
            return "reasoning.effort";
        }
        if (adapterType == AdapterType.AIHUBMIX_RESPONSES) {
            return "reasoning.responses-effort";
        }
        return null;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case AIHUBMIX_RESPONSES, AIHUBMIX_CHAT -> ProviderFeatureSets.ALL;
            case AIHUBMIX_MESSAGES -> ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
        };
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new AiHubMixResponsesModel(chatOptions(provider, apiKey, modelId),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatOptions(provider, apiKey, resolved.modelId());
        return switch (resolved.adapterType()) {
            case AIHUBMIX_RESPONSES -> new AiHubMixResponsesModel(options,
                webClientBuilder(provider));
            case AIHUBMIX_CHAT -> new AiHubMixChatModel(options, webClientBuilder(provider));
            case AIHUBMIX_MESSAGES -> new AiHubMixMessagesModel(options,
                webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported AIHubMix language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = AiHubMixEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .customHeaders(AiHubMixHeaders.DEFAULTS)
            .build();
        return new AiHubMixEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new AiHubMixRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider), AiHubMixHeaders.DEFAULTS);
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new AiHubMixImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId,
            AiHubMixHeaders.DEFAULTS), webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(StructuredOutputSupport.JSON_SCHEMA, true);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.AIHUBMIX_MESSAGES) {
            return languageModelProviderOptions(StructuredOutputSupport.PROMPT_ONLY, false);
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        StructuredOutputSupport structuredOutput, boolean nativeStrictToolSchemas) {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, nativeStrictToolSchemas, structuredOutput);
        return chatCompletionsProviderOptionsBuilder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
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
        return new EmbeddingModelProviderOptions(AiHubMixEmbeddingOptionsFactory::build);
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        var webClient = discoveryWebClientBuilder(provider)
            .baseUrl(modelCatalogBaseUrl(provider)).build();
        var request = webClient.get().uri(MODEL_CATALOG_PATH);
        if (apiKey != null && !apiKey.isBlank()) {
            request = request.header("Authorization", "Bearer " + apiKey);
        }
        request.header("APP-Code", AiHubMixHeaders.APP_CODE);
        customizeDiscoveryRequest(request);
        return request.retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
            })
            .map(root -> {
                var data = listValue(root, "data");
                return data == null ? List.of()
                    : discoveredModelsFromNodes(data, "model_id", this::toDiscoveredModel);
            });
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-completion-tokens"));
        return Map.copyOf(defaults);
    }

    private ChatCompletionsOptions chatOptions(AiProvider provider, String apiKey,
        String modelId) {
        return chatCompletionsOptions(provider, apiKey, modelId, AiHubMixHeaders.DEFAULTS);
    }

    private String modelCatalogBaseUrl(AiProvider provider) {
        var baseUrl = ProviderUris.withoutTrailingSlashes(resolveBaseUrl(provider));
        return baseUrl.endsWith("/v1")
            ? baseUrl.substring(0, baseUrl.length() - 3) : baseUrl;
    }

    private DiscoveredModel toDiscoveredModel(Map<?, ?> node) {
        var modelId = stringValue(node, "model_id");
        if (modelId.isBlank()) {
            modelId = stringValue(node, "id");
        }
        if (modelId.isBlank()) {
            return null;
        }
        if (type(node, "embedding")) {
            return typedModel(modelId, ModelType.EMBEDDING, AdapterType.AIHUBMIX_EMBEDDING);
        }
        if (type(node, "rerank", "reranking")) {
            return typedModel(modelId, ModelType.RERANK, AdapterType.RERANK);
        }
        if (type(node, "image_generation", "t2i")) {
            return imageModel(modelId, node);
        }
        if (type(node, "llm", "chat", "language", "t2t")) {
            return languageModel(modelId, node);
        }
        // video, TTS, and STT are separate public domains that AI Foundation does not expose.
        return null;
    }

    private DiscoveredModel typedModel(String modelId, ModelType type, AdapterType adapter) {
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(type)
            .adapterType(adapter)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .build();
    }

    private DiscoveredModel languageModel(String modelId, Map<?, ?> node) {
        var features = languageFeatures(node);
        var inputs = tokens(node.get("input_modalities"));
        var mediaTypes = new ArrayList<String>();
        if (inputs.contains("image")) {
            mediaTypes.add("image/*");
        }
        if (inputs.contains("pdf")) {
            mediaTypes.add("application/pdf");
        }
        var capability = LanguageCapability.builder()
            .imageInput(inputs.contains("image"))
            .fileInput(inputs.contains("pdf"))
            .reasoningHistory(features.contains(ModelFeature.REASONING))
            .inputMediaTypes(List.copyOf(mediaTypes))
            .inputSources(mediaTypes.isEmpty() ? List.of()
                : List.of(InputSource.DATA, InputSource.URL))
            .build();
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(ModelType.LANGUAGE)
            .features(features)
            .adapterType(AdapterType.AIHUBMIX_RESPONSES)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .capabilities(ModelCapabilities.builder().language(capability).build())
            .capabilitySources(ModelCapabilitySources.builder()
                .language(CapabilitySource.REMOTE)
                .build())
            .build();
    }

    private DiscoveredModel imageModel(String modelId, Map<?, ?> node) {
        var inputs = tokens(node.get("input_modalities"));
        var imageInput = inputs.contains("image");
        var capability = ImageGenerationCapability.builder()
            .textToImage(true)
            .imageToImage(imageInput)
            .build();
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(ModelType.IMAGE_GENERATION)
            .adapterType(AdapterType.AIHUBMIX_IMAGE)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .capabilities(ModelCapabilities.builder().imageGeneration(capability).build())
            .capabilitySources(ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.REMOTE)
                .build())
            .build();
    }

    private Set<ModelFeature> languageFeatures(Map<?, ?> node) {
        var values = tokens(node.get("features"));
        var inputs = tokens(node.get("input_modalities"));
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        if (inputs.contains("image")) {
            features.add(ModelFeature.VISION);
        }
        if (containsAny(values, "tools", "function_calling", "web", "deepsearch")) {
            features.add(ModelFeature.TOOL_CALL);
        }
        if (values.contains("structured_outputs")) {
            features.add(ModelFeature.STRUCTURED_OUTPUT);
        }
        if (values.contains("thinking")) {
            features.add(ModelFeature.REASONING);
        }
        return Set.copyOf(features);
    }

    private boolean type(Map<?, ?> node, String... expected) {
        var declaredTypes = tokens(node.get("types"));
        var declaredType = tokens(node.get("type"));
        for (var value : expected) {
            if (declaredTypes.contains(value)) {
                return true;
            }
            if (declaredType.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(Set<String> values, String... expected) {
        for (var value : expected) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokens(Object value) {
        if (value == null) {
            return Set.of();
        }
        var result = new LinkedHashSet<String>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addTokens(result, item));
        } else {
            addTokens(result, value);
        }
        return Set.copyOf(result);
    }

    private void addTokens(Set<String> target, Object value) {
        if (value == null) {
            return;
        }
        for (var token : value.toString().split("[,\\s]+")) {
            if (!token.isBlank()) {
                target.add(token.toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    private String reasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "reasoning", "reasoning_content")) {
            var value = message.getMetadata().get(key);
            if (value == null) {
                continue;
            }
            if (value.toString().isBlank()) {
                continue;
            }
            return value.toString();
        }
        return null;
    }

}
