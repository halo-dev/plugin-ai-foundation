package run.halo.aifoundation.provider.gitee;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** Provider-owned integration for Gitee AI (formerly MoArk). */
@Component
public class GiteeProvider extends AbstractAiProviderType {

    public static final String FAILOVER_HEADER = "X-Failover-Enabled";
    private static final String DEFAULT_BASE_URL = "https://ai.gitee.com/v1";

    @Override
    public String getProviderType() {
        // Persisted providers already use this type. The Java package can evolve independently.
        return "gitee-moark";
    }

    @Override
    public String getDisplayName() {
        return "Gitee 模力方舟";
    }

    @Override
    public String getDescription() {
        return "Gitee AI 原生模型服务，支持 Chat、Responses、多模态向量、重排和图像生成。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/gitee-moark.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://ai.gitee.com/";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://ai.gitee.com/docs/products/apis/texts/text-generation";
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
        return List.of(AdapterType.GITEE_CHAT, AdapterType.GITEE_RESPONSES,
            AdapterType.GITEE_MESSAGES, AdapterType.GITEE_EMBEDDING, AdapterType.RERANK,
            AdapterType.GITEE_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        // Gitee is a model marketplace. This is a provider ceiling; discovery narrows each model.
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        if (adapterType == AdapterType.GITEE_MESSAGES) {
            return List.of(ModelFeature.STREAMING, ModelFeature.STRUCTURED_OUTPUT);
        }
        if (adapterType == AdapterType.GITEE_CHAT) {
            return ProviderFeatureSets.VISION_REASONING;
        }
        if (adapterType == AdapterType.GITEE_RESPONSES) {
            return ProviderFeatureSets.VISION_REASONING;
        }
        return List.of();
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 1000;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new GiteeChatModel(chatOptions(provider, apiKey, modelId),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatOptions(provider, apiKey, resolved.modelId());
        return switch (resolved.adapterType()) {
            case GITEE_CHAT -> new GiteeChatModel(options, webClientBuilder(provider));
            case GITEE_RESPONSES -> new GiteeResponsesModel(options, webClientBuilder(provider));
            case GITEE_MESSAGES -> new GiteeMessagesModel(options, webClientBuilder(provider));
            default -> throw new IllegalArgumentException("Unsupported Gitee AI language adapter: "
                + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = GiteeEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .customHeaders(failoverHeaders())
            .build();
        return new GiteeEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new GiteeRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new GiteeImageGenerationClient(new ImageGenerationClientOptions(getProviderType(),
            resolveBaseUrl(provider), apiKey, modelId, failoverHeaders()),
            webClientBuilder(provider));
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(GiteeEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(StructuredOutputSupport.JSON_SCHEMA);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.GITEE_MESSAGES) {
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
            .reasoningHistorySupported(false)
            .streamToolCallsForReasoning(false)
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(structuredOutput)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .build();
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/models")
                .queryParam("include_details", true).build(), null)
            .map(root -> {
                var data = listValue(root, "data");
                return data == null ? List.of()
                    : discoveredModelsFromNodes(data, "id", this::discoveredModel);
            });
    }

    private DiscoveredModel discoveredModel(Map<?, ?> node) {
        var modelId = stringValue(node, "id");
        var operations = operations(node);
        var modelType = modelType(operations);
        if (modelId.isBlank()) {
            return null;
        }
        if (modelType == null) {
            return null;
        }
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(modelType)
            .features(features(modelType, operations))
            .adapterType(recommendAdapterType(modelType).orElse(null))
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .build();
    }

    private Set<String> operations(Map<?, ?> node) {
        var result = new LinkedHashSet<String>();
        var values = node.get("operations") instanceof Iterable<?> iterable
            ? iterable : List.of();
        for (var value : values) {
            if (!(value instanceof Map<?, ?> operation)) {
                continue;
            }
            addOperationFields(operation, result);
        }
        return Set.copyOf(result);
    }

    private void addOperationFields(Map<?, ?> operation, Set<String> result) {
        for (var key : List.of("type", "name", "path")) {
            var field = operation.get(key);
            if (field == null) {
                continue;
            }
            result.add(field.toString().toLowerCase(Locale.ROOT));
        }
    }

    private ModelType modelType(Set<String> operations) {
        if (contains(operations, "rerank", "sentence_similarity")) {
            return ModelType.RERANK;
        }
        if (contains(operations, "embedding")) {
            return ModelType.EMBEDDING;
        }
        if (contains(operations, "text2image", "image2image", "/images/")) {
            return ModelType.IMAGE_GENERATION;
        }
        if (contains(operations, "text2text", "completions", "image2text",
            "/responses")) {
            return ModelType.LANGUAGE;
        }
        return null;
    }

    private Set<ModelFeature> features(ModelType modelType, Set<String> operations) {
        if (modelType != ModelType.LANGUAGE) {
            return Set.of();
        }
        var result = new LinkedHashSet<ModelFeature>();
        result.add(ModelFeature.STREAMING);
        if (contains(operations, "image2text")) {
            result.add(ModelFeature.VISION);
        }
        return Set.copyOf(result);
    }

    private boolean contains(Set<String> values, String... tokens) {
        for (var value : values) {
            for (var token : tokens) {
                if (value.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        for (var parameter : List.of(ModelParameter.MIN_P, ModelParameter.REPETITION_PENALTY,
            ModelParameter.PARALLEL_TOOL_CALLS, ModelParameter.IMAGE_SEED,
            ModelParameter.NEGATIVE_PROMPT)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }

    private run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions
        chatOptions(AiProvider provider, String apiKey, String modelId) {
        return chatCompletionsOptions(provider, apiKey, modelId, failoverHeaders());
    }

    private Map<String, String> failoverHeaders() {
        // Gitee can route to a different compute model and bill that successful model. Keep this
        // request-scoped and opt-in: callers can override the header to true when desired.
        return Map.of(FAILOVER_HEADER, "false");
    }
}
