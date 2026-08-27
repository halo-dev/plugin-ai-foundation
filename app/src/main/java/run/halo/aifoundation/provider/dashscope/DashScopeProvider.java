package run.halo.aifoundation.provider.dashscope;

import java.net.URI;
import java.util.ArrayList;
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
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

@Component
public class DashScopeProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final int DISCOVERY_PAGE_SIZE = 100;

    @Override
    public String getProviderType() {
        return "dashscope";
    }

    @Override
    public String getDisplayName() {
        return "阿里云百炼";
    }

    @Override
    public String getDescription() {
        return "阿里云百炼模型平台，支持千问对话、原生向量、重排序和万相图像。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/dashscope.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://bailian.aliyun.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://help.aliyun.com/zh/model-studio/";
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
        return List.of(AdapterType.DASHSCOPE_CHAT, AdapterType.DASHSCOPE_RESPONSES,
            AdapterType.DASHSCOPE_MESSAGES,
            AdapterType.DASHSCOPE_EMBEDDING,
            AdapterType.RERANK, AdapterType.DASHSCOPE_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.ALL;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case DASHSCOPE_CHAT -> ProviderFeatureSets.ALL;
            case DASHSCOPE_RESPONSES, DASHSCOPE_MESSAGES ->
                ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
        };
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.dashscope";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case DASHSCOPE_CHAT -> "reasoning.dashscope";
            case DASHSCOPE_RESPONSES -> "reasoning.responses-effort";
            case DASHSCOPE_MESSAGES -> "reasoning.dashscope-messages";
            default -> null;
        };
    }

    @Override
    public int maxEmbeddingsPerCall() {
        // Conservative protocol-wide limit; model configuration may further restrict batching.
        return 10;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        var endpoints = endpoints(provider);
        var options = chatCompletionsOptions(provider, apiKey, modelId, Map.of()).mutate()
            .baseUrl(endpoints.compatibleBaseUrl())
            .endpointPath("/chat/completions")
            .build();
        return new DashScopeChatModel(options, webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var endpoints = endpoints(provider);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of()).mutate()
            .baseUrl(endpoints.compatibleBaseUrl())
            .build();
        return switch (resolved.adapterType()) {
            case DASHSCOPE_CHAT -> new DashScopeChatModel(options.mutate()
                .endpointPath("/chat/completions").build(), webClientBuilder(provider));
            case DASHSCOPE_RESPONSES -> new DashScopeResponsesModel(options,
                webClientBuilder(provider));
            case DASHSCOPE_MESSAGES -> new DashScopeMessagesModel(options.mutate()
                .baseUrl(endpoints.messagesBaseUrl()).build(), webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported DashScope language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = DashScopeEmbeddingOptions.builder()
            .baseUrl(endpoints(provider).nativeBaseUrl())
            .apiKey(apiKey)
            .model(modelId)
            .outputType(DashScopeEmbeddingOptions.OutputType.DENSE)
            .build();
        return new DashScopeEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new DashScopeRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new DashScopeImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, false, StructuredOutputSupport.JSON_SCHEMA);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(StructuredOutputSupport.JSON_SCHEMA)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("dashscope",
            DashScopeEmbeddingOptionsFactory::build);
    }

    @Override
    public RerankingModelProviderOptions rerankingModelProviderOptions() {
        return RerankingModelProviderOptions.builder()
            .providerOptionsSupported(true)
            .build();
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoveryPage(provider, apiKey, 1)
            .expand(page -> page.hasNext()
                ? discoveryPage(provider, apiKey, page.pageNumber() + 1) : Mono.empty())
            .flatMapIterable(DiscoveryPage::models)
            .collectList();
    }

    private Mono<DiscoveryPage> discoveryPage(AiProvider provider, String apiKey,
        int pageNumber) {
        var url = endpoints(provider).modelCatalogUrl() + "?page_no=" + pageNumber
            + "&page_size=" + DISCOVERY_PAGE_SIZE;
        return getDiscoveryJson(provider, apiKey, ignored -> URI.create(url), null)
            .map(json -> {
                var output = json.get("output") instanceof Map<?, ?> value ? value : Map.of();
                var nodes = output.get("models") instanceof List<?> value ? value : List.of();
                var models = discoveredModels(nodes);
                var total = integerValue(output.get("total"), models.size());
                var pageSize = integerValue(output.get("page_size"), DISCOVERY_PAGE_SIZE);
                return new DiscoveryPage(pageNumber, pageSize, total, models);
            });
    }

    private List<DiscoveredModel> discoveredModels(List<?> nodes) {
        var models = new ArrayList<DiscoveredModel>();
        for (var item : nodes) {
            if (!(item instanceof Map<?, ?> node)) {
                continue;
            }
            var model = discoveredModel(node);
            if (model != null) {
                models.add(model);
            }
        }
        return List.copyOf(models);
    }

    private DiscoveredModel discoveredModel(Map<?, ?> node) {
        var modelId = stringValue(node, "model");
        if (modelId.isBlank()) {
            return null;
        }
        var modelType = dashScopeModelType(node);
        if (modelType == null) {
            return null;
        }
        var features = dashScopeFeatures(node, modelType);
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(stringValue(node, "name"))
            .modelType(modelType)
            .features(features)
            .adapterType(recommendAdapterType(modelType).orElse(null))
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .build();
    }

    private ModelType dashScopeModelType(Map<?, ?> node) {
        var capabilities = normalizedValues(node.get("capabilities"));
        if (contains(capabilities, "rerank")) {
            return ModelType.RERANK;
        }
        if (contains(capabilities, "embedding", "tr", "me")) {
            return ModelType.EMBEDDING;
        }
        if (contains(capabilities, "imagegeneration", "texttoimage", "ig")) {
            return ModelType.IMAGE_GENERATION;
        }
        if (contains(capabilities, "textgeneration", "chat", "language", "tg")) {
            return ModelType.LANGUAGE;
        }
        return null;
    }

    private Set<ModelFeature> dashScopeFeatures(Map<?, ?> node, ModelType modelType) {
        if (modelType != ModelType.LANGUAGE) {
            return Set.of();
        }
        var values = new LinkedHashSet<String>();
        values.addAll(normalizedValues(node.get("capabilities")));
        values.addAll(normalizedValues(node.get("features")));
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        if (contains(values, "reasoning")) {
            features.add(ModelFeature.REASONING);
        }
        if (contains(values, "vision", "multimodal", "vu")) {
            features.add(ModelFeature.VISION);
        }
        if (contains(values, "audiounderstanding", "audio")) {
            features.add(ModelFeature.AUDIO_INPUT);
        }
        if (contains(values, "functioncalling", "toolcall", "tools")) {
            features.add(ModelFeature.TOOL_CALL);
        }
        if (contains(values, "structuredoutput", "structuredoutputs", "jsonschema")) {
            features.add(ModelFeature.STRUCTURED_OUTPUT);
        }
        return Set.copyOf(features);
    }

    private Set<String> normalizedValues(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return Set.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (var item : values) {
            if (item != null) {
                normalized.add(item.toString().replace("_", "")
                    .replace("-", "").toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    private boolean contains(Set<String> values, String... candidates) {
        for (var candidate : candidates) {
            var normalized = candidate.replace("_", "").replace("-", "")
                .toLowerCase(Locale.ROOT);
            if (values.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private int integerValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
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

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.TOP_N,
            DefaultParameterMapping.template("rerank.parameters.top-n"));
        return Map.copyOf(defaults);
    }

    private DashScopeEndpointResolver endpoints(AiProvider provider) {
        return new DashScopeEndpointResolver(resolveBaseUrl(provider));
    }

    private record DiscoveryPage(int pageNumber, int pageSize, int total,
                                 List<DiscoveredModel> models) {
        boolean hasNext() {
            return pageNumber * pageSize < total;
        }
    }
}
