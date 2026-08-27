package run.halo.aifoundation.provider;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptionsFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.transport.ProviderHttpClientFactory;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ProviderParameterMappingDefaults;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;

@Slf4j
public abstract class AbstractAiProviderType implements AiProviderType {

    protected static final String COMPLETIONS_PATH = "/chat/completions";
    private static final List<String> MODEL_TYPE_FIELDS = List.of(
        "type", "model_type", "modelType", "capabilities", "features",
        "supported_endpoint_types");
    private static final List<String> CAPABILITY_FIELDS = List.of("capabilities", "features");

    @Override
    public String getCompletionsPath() {
        return COMPLETIONS_PATH;
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults =
            new java.util.EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(ProviderParameterMappingDefaults.forAdapters(getSupportedAdapterTypes()));
        var reasoningTemplate = defaultReasoningMappingTemplate();
        if (reasoningTemplate != null && !reasoningTemplate.isBlank()) {
            defaults.put(ModelParameter.REASONING,
                DefaultParameterMapping.template(reasoningTemplate));
        }
        return Map.copyOf(defaults);
    }

    /**
     * Returns the provider's current protocol-level reasoning mapping.
     *
     * <p>Model mappings remain authoritative and may replace this default when a model generation
     * uses a different wire shape. Provider implementations must not select this value by
     * inspecting a model identifier.</p>
     */
    protected String defaultReasoningMappingTemplate() {
        return null;
    }

    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return defaultReasoningMappingTemplate();
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings(
        AdapterType adapterType) {
        var defaults = new java.util.EnumMap<ModelParameter, DefaultParameterMapping>(
            ModelParameter.class);
        defaults.putAll(getDefaultParameterMappings());
        if (adapterType == null || adapterType.getModelType() != ModelType.LANGUAGE) {
            return Map.copyOf(defaults);
        }
        var reasoningTemplate = defaultReasoningMappingTemplate(adapterType);
        if (reasoningTemplate == null || reasoningTemplate.isBlank()) {
            defaults.put(ModelParameter.REASONING, DefaultParameterMapping.unsupported());
            return Map.copyOf(defaults);
        }
        defaults.put(ModelParameter.REASONING,
            DefaultParameterMapping.template(reasoningTemplate));
        return Map.copyOf(defaults);
    }

    protected String resolveBaseUrl(AiProvider provider) {
        var spec = provider.getSpec();
        var baseUrl = spec.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        var defaultUrl = getDefaultBaseUrl();
        if (defaultUrl == null || defaultUrl.isBlank()) {
            throw new IllegalArgumentException(
                "baseUrl is required for " + getProviderType()
                    + " provider: " + provider.getMetadata().getName());
        }
        return defaultUrl;
    }

    protected WebClient.Builder webClientBuilder(AiProvider provider) {
        return ProviderHttpClientFactory.webClientBuilder(provider);
    }

    protected WebClient.Builder discoveryWebClientBuilder(AiProvider provider) {
        return ProviderHttpClientFactory.discoveryWebClientBuilder(provider);
    }

    protected RestClient.Builder restClientBuilder(AiProvider provider) {
        return ProviderHttpClientFactory.restClientBuilder(provider);
    }

    protected LanguageModelProviderOptions chatCompletionsLanguageModelProviderOptions(
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        return chatCompletionsLanguageModelProviderOptions(reasoningControlOptions,
            extraBodyCustomizer, false);
    }

    protected LanguageModelProviderOptions chatCompletionsLanguageModelProviderOptions(
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer,
        boolean nativeStrictToolSchemas) {
        var optionsFactory = ChatCompletionsOptionsFactory
            .builder(getProviderType(), reasoningControlOptions)
            .extraBodyCustomizer(extraBodyCustomizer)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
            .structuredOutputSupport(StructuredOutputSupport.JSON_SCHEMA)
            .build();
        return LanguageModelProviderOptions.builder()
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
            .structuredOutputSupport(StructuredOutputSupport.JSON_SCHEMA)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoningControlOptions)
            .build();
    }

    protected ChatCompletionsOptionsFactory chatCompletionsOptionsFactory(
        ReasoningControlOptions reasoningControlOptions, boolean nativeStrictToolSchemas,
        StructuredOutputSupport structuredOutputSupport) {
        return ChatCompletionsOptionsFactory.builder(getProviderType(), reasoningControlOptions)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
            .structuredOutputSupport(structuredOutputSupport)
            .build();
    }

    protected ChatCompletionsOptions chatCompletionsOptions(AiProvider provider, String apiKey,
        String modelId, Map<String, String> customHeaders) {
        var builder = ChatCompletionsOptions.builder();
        builder.baseUrl(resolveBaseUrl(provider))
            .endpointPath(chatCompletionsEndpointPath(provider))
            .apiKey(apiKey)
            .model(modelId);
        applyChatCompletionsClientOptions(builder, provider, customHeaders);
        return builder.build();
    }

    protected String chatCompletionsEndpointPath(AiProvider provider) {
        var path = supportsChatEndpointOverrides()
            ? provider.getSpec().getChatEndpointPath() : null;
        if (path == null || path.isBlank()) {
            path = getChatEndpointPath();
        }
        if (path == null || path.isBlank()) {
            path = COMPLETIONS_PATH;
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    protected boolean supportsChatEndpointOverrides() {
        return false;
    }

    private void applyChatCompletionsClientOptions(ChatCompletionsOptions.Builder builder, AiProvider provider,
        Map<String, String> customHeaders) {
        var proxy = chatCompletionsProxy(provider);
        if (proxy != null) {
            builder.proxy(proxy);
        }
        if (customHeaders != null && !customHeaders.isEmpty()) {
            builder.customHeaders(Map.copyOf(customHeaders));
        }
    }

    private java.net.Proxy chatCompletionsProxy(AiProvider provider) {
        var spec = provider != null ? provider.getSpec() : null;
        if (spec == null) {
            return null;
        }
        if (spec.getProxyHost() == null) {
            return null;
        }
        if (spec.getProxyHost().isBlank()) {
            return null;
        }
        if (spec.getProxyPort() == null) {
            return null;
        }
        return new java.net.Proxy(java.net.Proxy.Type.HTTP,
            new java.net.InetSocketAddress(spec.getProxyHost().trim(), spec.getProxyPort()));
    }

    protected reactor.netty.http.client.HttpClient httpClient(AiProvider provider) {
        return ProviderHttpClientFactory.httpClient(provider);
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return null;
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoverDataArrayModels(provider, apiKey);
    }

    /**
     * Discovers an identifier-only provider catalog without treating adapter capabilities as
     * evidence about every returned model.
     */
    protected Mono<List<DiscoveredModel>> discoverIdentifierOnlyModels(AiProvider provider,
        String apiKey, String path) {
        return discoverDataArrayModels(provider, apiKey, path,
            node -> providerDefaultModelProfile(stringValue(node, "id")));
    }

    protected Mono<List<DiscoveredModel>> discoverDataArrayModels(AiProvider provider,
        String apiKey) {
        return discoverDataArrayModels(provider, apiKey, "/models",
            node -> modelProfile(node, stringValue(node, "id")));
    }

    private Mono<List<DiscoveredModel>> discoverDataArrayModels(AiProvider provider,
        String apiKey, String path, Function<Map<?, ?>, DiscoveredModel> profileMapper) {
        var baseUrl = resolveBaseUrl(provider);
        var providerName = provider.getMetadata().getName();
        log.info("Discovering models for provider {}: type={}, baseUrl={}",
            providerName, getProviderType(), baseUrl);

        return getDiscoveryJson(provider, apiKey, uriBuilder -> uriBuilder.path(path).build(),
                this::customizeDiscoveryRequest)
            .map(json -> {
                var dataList = listValue(json, "data");
                if (dataList == null) {
                    log.warn("Provider API response missing 'data' array for {}", providerName);
                    return List.<DiscoveredModel>of();
                }
                var models = discoveredModelsFromNodes(dataList, "id", profileMapper);
                log.info("Discovered {} models for provider {}", models.size(), providerName);
                return models;
            });
    }

    protected DiscoveredModel modelProfile(Map<?, ?> node, String modelId) {
        var explicitType = explicitModelType(node);
        if (explicitType != null) {
            return remoteDiscoveredModel(modelId, explicitType,
                explicitFeatures(node, explicitType),
                recommendAdapterType(explicitType).orElse(null));
        }
        return providerDefaultModelProfile(modelId);
    }

    protected DiscoveredModel providerDefaultModelProfile(String modelId) {
        var modelType = defaultDiscoveryModelType();
        if (modelType == null) {
            return null;
        }
        var adapter = recommendAdapterType(modelType).orElse(null);
        var features = defaultDiscoveredModelFeatures(adapter);
        return discoveredModel(modelId, modelType, features, adapter,
            DiscoverySource.RULE, DiscoveryConfidence.LOW);
    }

    private Set<ModelFeature> defaultDiscoveredModelFeatures(AdapterType adapterType) {
        if (adapterType == null) {
            return Set.of();
        }
        return Set.copyOf(getSupportedFeatures(adapterType));
    }

    protected ModelType defaultDiscoveryModelType() {
        var modelTypes = getSupportedModelTypes();
        if (modelTypes.size() == 1) {
            return modelTypes.getFirst();
        }
        if (modelTypes.contains(ModelType.LANGUAGE)) {
            return ModelType.LANGUAGE;
        }
        return null;
    }

    protected ModelType explicitModelType(Map<?, ?> node) {
        if (node == null) {
            return null;
        }
        if (fieldsContainAnyToken(node, MODEL_TYPE_FIELDS, "rerank", "reranker")) {
            return ModelType.RERANK;
        }
        if (fieldsContainAnyToken(node, MODEL_TYPE_FIELDS, "embedding")) {
            return ModelType.EMBEDDING;
        }
        if (fieldsContainAnyToken(node, MODEL_TYPE_FIELDS, "chat", "language")) {
            return ModelType.LANGUAGE;
        }
        return null;
    }

    protected DiscoveredModel discoveredModel(String modelId, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType, DiscoverySource source,
        DiscoveryConfidence confidence) {
        var resolvedAdapter = adapterType;
        if (resolvedAdapter == null) {
            resolvedAdapter = recommendAdapterType(modelType).orElse(null);
        }
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(modelType)
            .features(features)
            .adapterType(resolvedAdapter)
            .source(source)
            .confidence(confidence)
            .build();
    }

    protected DiscoveredModel remoteDiscoveredModel(String modelId, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType) {
        return discoveredModel(modelId, modelType, features, adapterType, DiscoverySource.REMOTE,
            DiscoveryConfidence.HIGH);
    }

    protected Set<ModelFeature> explicitFeatures(Map<?, ?> node, ModelType modelType) {
        if (modelType != ModelType.LANGUAGE) {
            return Set.of();
        }
        var features = new LinkedHashSet<ModelFeature>();
        if (fieldsContainAnyToken(node, CAPABILITY_FIELDS, "streaming")) {
            features.add(ModelFeature.STREAMING);
        }
        if (fieldsContainAnyToken(node, CAPABILITY_FIELDS, "vision", "multimodal")) {
            features.add(ModelFeature.VISION);
        }
        if (fieldsContainAnyToken(node, CAPABILITY_FIELDS, "reasoning", "thinking")) {
            features.add(ModelFeature.REASONING);
        }
        if (fieldsContainAnyToken(node, CAPABILITY_FIELDS, "tools", "functioncalling")) {
            features.add(ModelFeature.TOOL_CALL);
        }
        return Set.copyOf(features);
    }

    protected void customizeDiscoveryRequest(
        WebClient.RequestHeadersSpec<?> requestSpec) {
    }

    protected Mono<Map<String, Object>> getDiscoveryJson(AiProvider provider, String apiKey,
        Function<UriBuilder, URI> uriFunction,
        Consumer<WebClient.RequestHeadersSpec<?>> requestCustomizer) {
        var wc = discoveryWebClientBuilder(provider).baseUrl(resolveBaseUrl(provider)).build();
        var requestSpec = wc.get().uri(uriFunction);

        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        if (requestCustomizer != null) {
            requestCustomizer.accept(requestSpec);
        }

        return requestSpec
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    protected List<DiscoveredModel> discoveredModelsFromNodes(List<?> nodes, String idField,
        Function<Map<?, ?>, DiscoveredModel> mapper) {
        var models = new ArrayList<DiscoveredModel>();
        for (var item : nodes) {
            if (!(item instanceof Map<?, ?> node)) {
                continue;
            }
            var modelId = stringValue(node, idField);
            if (modelId.isBlank()) {
                continue;
            }
            var model = mapper.apply(node);
            if (model == null) {
                continue;
            }
            models.add(model);
        }
        return models;
    }

    protected List<?> listValue(Map<?, ?> node, String field) {
        var value = node.get(field);
        return value instanceof List<?> list ? list : null;
    }

    protected String stringValue(Map<?, ?> node, String field) {
        var value = node.get(field);
        return value != null ? value.toString() : "";
    }

    protected boolean booleanValue(Map<?, ?> node, String field) {
        var value = node.get(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    protected boolean containsToken(Object value, String expected) {
        if (value == null) {
            return false;
        }
        if (expected == null) {
            return false;
        }
        var normalizedExpected = expected.toLowerCase(Locale.ROOT);
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> containsToken(item, normalizedExpected));
        }
        var normalized = value.toString().toLowerCase(Locale.ROOT);
        if (normalized.equals(normalizedExpected)) {
            return true;
        }
        return normalized.contains(normalizedExpected);
    }

    private boolean containsAnyToken(Object value, String... expected) {
        if (expected == null) {
            return false;
        }
        for (var token : expected) {
            if (containsToken(value, token)) {
                return true;
            }
        }
        return false;
    }

    private boolean fieldsContainAnyToken(Map<?, ?> node, List<String> fields,
        String... expected) {
        for (var field : fields) {
            if (containsAnyToken(node.get(field), expected)) {
                return true;
            }
        }
        return false;
    }
}
