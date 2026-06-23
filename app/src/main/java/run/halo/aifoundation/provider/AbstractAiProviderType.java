package run.halo.aifoundation.provider;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
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
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleEmbeddingOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider.Proxy;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.openai.OpenAiChatOptionsSupport;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatModel;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleEmbeddingModel;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Slf4j
public abstract class AbstractAiProviderType implements AiProviderType {

    private static final int DISCOVERY_MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024;
    private static final String COMPLETIONS_PATH = "/chat/completions";

    @Override
    public String getCompletionsPath() {
        return COMPLETIONS_PATH;
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
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                httpClient(provider)
            ));
    }

    protected WebClient.Builder discoveryWebClientBuilder(AiProvider provider) {
        return webClientBuilder(provider)
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                    .maxInMemorySize(DISCOVERY_MAX_IN_MEMORY_SIZE))
                .build());
    }

    protected RestClient.Builder restClientBuilder(AiProvider provider) {
        return RestClient.builder()
            .requestFactory(new ReactorClientHttpRequestFactory(
                httpClient(provider)
            ));
    }

    protected ChatModel buildOpenAiCompatibleChatModel(AiProvider provider, String apiKey,
        String modelId) {
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId, Map.of());
    }

    protected ChatModel buildOpenAiCompatibleChatModel(AiProvider provider, String apiKey,
        String modelId, Map<String, String> customHeaders) {
        var options = openAiChatOptions(provider, apiKey, modelId, customHeaders);
        return new OpenAiCompatibleChatModel(options, webClientBuilder(provider));
    }

    protected EmbeddingModel buildOpenAiCompatibleEmbeddingModel(AiProvider provider,
        String apiKey, String modelId) {
        return buildOpenAiCompatibleEmbeddingModel(provider, apiKey, modelId, Map.of());
    }

    protected EmbeddingModel buildOpenAiCompatibleEmbeddingModel(AiProvider provider,
        String apiKey, String modelId, Map<String, String> customHeaders) {
        return new OpenAiCompatibleEmbeddingModel(openAiEmbeddingOptions(provider, apiKey,
            modelId, customHeaders), webClientBuilder(provider));
    }

    protected LanguageModelProviderOptions openAiCompatibleLanguageModelProviderOptions(
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer) {
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions,
            extraBodyCustomizer, false);
    }

    protected LanguageModelProviderOptions openAiCompatibleLanguageModelProviderOptions(
        ReasoningControlOptions reasoningControlOptions,
        BiConsumer<Map<String, Object>, GenerateTextRequest> extraBodyCustomizer,
        boolean nativeStrictToolSchemas) {
        return LanguageModelProviderOptions.builder()
            .requestHeadersSupported(true)
            .seedSupported(true)
            .chatOptionsFactory(request -> OpenAiChatOptionsSupport.buildBasic(request,
                getProviderType(), reasoningControlOptions, extraBodyCustomizer))
            .toolCallingChatOptionsFactory((request, toolCallbacks, toolNames) ->
                OpenAiChatOptionsSupport.buildToolCalling(request, toolCallbacks, toolNames,
                    getProviderType(), reasoningControlOptions, extraBodyCustomizer,
                    nativeStrictToolSchemas))
            .structuredOutputChatOptionsFactory(request -> OpenAiChatOptionsSupport.buildStructured(
                request, getProviderType(), reasoningControlOptions, extraBodyCustomizer))
            .reasoningControlOptions(reasoningControlOptions)
            .build();
    }

    protected OpenAiCompatibleChatOptions openAiChatOptions(AiProvider provider, String apiKey,
        String modelId, Map<String, String> customHeaders) {
        var builder = OpenAiCompatibleChatOptions.builder();
        builder.baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId);
        applyOpenAiClientOptions(builder, provider, customHeaders);
        return builder.build();
    }

    protected OpenAiCompatibleEmbeddingOptions openAiEmbeddingOptions(AiProvider provider, String apiKey,
        String modelId, Map<String, String> customHeaders) {
        var builder = OpenAiCompatibleEmbeddingOptions.builder();
        builder.baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId);
        applyOpenAiClientOptions(builder, provider, customHeaders);
        return builder.build();
    }

    private void applyOpenAiClientOptions(OpenAiCompatibleChatOptions.Builder builder, AiProvider provider,
        Map<String, String> customHeaders) {
        var proxy = openAiProxy(provider);
        if (proxy != null) {
            builder.proxy(proxy);
        }
        if (customHeaders != null && !customHeaders.isEmpty()) {
            builder.customHeaders(Map.copyOf(customHeaders));
        }
    }

    private void applyOpenAiClientOptions(OpenAiCompatibleEmbeddingOptions.Builder builder,
        AiProvider provider, Map<String, String> customHeaders) {
        var proxy = openAiProxy(provider);
        if (proxy != null) {
            builder.proxy(proxy);
        }
        if (customHeaders != null && !customHeaders.isEmpty()) {
            builder.customHeaders(Map.copyOf(customHeaders));
        }
    }

    private java.net.Proxy openAiProxy(AiProvider provider) {
        var spec = provider != null ? provider.getSpec() : null;
        if (spec == null || spec.getProxyHost() == null || spec.getProxyHost().isBlank()
            || spec.getProxyPort() == null) {
            return null;
        }
        return new java.net.Proxy(java.net.Proxy.Type.HTTP,
            new java.net.InetSocketAddress(spec.getProxyHost().trim(), spec.getProxyPort()));
    }

    protected HttpClient httpClient(AiProvider provider) {
        var client = HttpClient.create()
            .responseTimeout(Duration.ofMinutes(5))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000);

        var spec = provider != null ? provider.getSpec() : null;
        if (spec == null) {
            return client;
        }

        var proxyHost = spec.getProxyHost();
        var proxyPort = spec.getProxyPort();
        if (proxyHost == null || proxyHost.isBlank() || proxyPort == null) {
            return client;
        }

        return client.proxy(proxy -> proxy
            .type(Proxy.HTTP)
            .host(proxyHost.trim())
            .port(proxyPort)
        );
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return null;
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoverOpenAiCompatibleModels(provider, apiKey);
    }

    protected Mono<List<DiscoveredModel>> discoverOpenAiCompatibleModels(AiProvider provider,
        String apiKey) {
        var baseUrl = resolveBaseUrl(provider);
        var providerName = provider.getMetadata().getName();
        log.info("Discovering models for provider {}: type={}, baseUrl={}",
            providerName, getProviderType(), baseUrl);

        return getDiscoveryJson(provider, apiKey, uriBuilder -> uriBuilder.path("/models")
                .build(), this::customizeDiscoveryRequest)
            .map(json -> {
                var dataList = listValue(json, "data");
                if (dataList == null) {
                    log.warn("Provider API response missing 'data' array for {}", providerName);
                    return List.<DiscoveredModel>of();
                }
                var models = discoveredModelsFromNodes(dataList, "id",
                    node -> modelProfile(node, stringValue(node, "id")));
                log.info("Discovered {} models for provider {}", models.size(), providerName);
                return models;
            });
    }

    protected DiscoveredModel inferModelProfile(String modelId) {
        var modelType = inferModelType(modelId);
        var features = inferFeatures(modelType, modelId);
        return discoveredModel(modelId, modelType, features,
            recommendAdapterType(modelType).orElse(null), DiscoverySource.RULE,
            DiscoveryConfidence.LOW);
    }

    protected DiscoveredModel modelProfile(Map<?, ?> node, String modelId) {
        var explicitType = explicitModelType(node);
        if (explicitType == null) {
            return inferModelProfile(modelId);
        }
        return remoteDiscoveredModel(modelId, explicitType, inferFeatures(explicitType, modelId),
            recommendAdapterType(explicitType).orElse(null));
    }

    protected ModelType explicitModelType(Map<?, ?> node) {
        if (node == null) {
            return null;
        }
        if (containsAnyToken(node.get("type"), "rerank", "reranker")
            || containsAnyToken(node.get("model_type"), "rerank", "reranker")
            || containsAnyToken(node.get("modelType"), "rerank", "reranker")
            || containsAnyToken(node.get("capabilities"), "rerank", "reranker")
            || containsAnyToken(node.get("features"), "rerank", "reranker")
            || containsAnyToken(node.get("supported_endpoint_types"), "rerank", "reranker")) {
            return ModelType.RERANK;
        }
        if (containsAnyToken(node.get("type"), "embedding")
            || containsAnyToken(node.get("model_type"), "embedding")
            || containsAnyToken(node.get("modelType"), "embedding")
            || containsAnyToken(node.get("capabilities"), "embedding")
            || containsAnyToken(node.get("features"), "embedding")
            || containsAnyToken(node.get("supported_endpoint_types"), "embedding")) {
            return ModelType.EMBEDDING;
        }
        if (containsAnyToken(node.get("type"), "chat", "language")
            || containsAnyToken(node.get("model_type"), "chat", "language")
            || containsAnyToken(node.get("modelType"), "chat", "language")
            || containsAnyToken(node.get("capabilities"), "chat", "language")
            || containsAnyToken(node.get("features"), "chat", "language")
            || containsAnyToken(node.get("supported_endpoint_types"), "chat", "language")) {
            return ModelType.LANGUAGE;
        }
        return null;
    }

    protected DiscoveredModel discoveredModel(String modelId, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType, DiscoverySource source,
        DiscoveryConfidence confidence) {
        return new DiscoveredModel(
            modelId,
            modelId,
            modelType,
            features,
            adapterType != null ? adapterType : recommendAdapterType(modelType).orElse(null),
            source,
            confidence
        );
    }

    protected DiscoveredModel remoteDiscoveredModel(String modelId, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType) {
        return discoveredModel(modelId, modelType, features, adapterType, DiscoverySource.REMOTE,
            DiscoveryConfidence.HIGH);
    }

    protected ModelType inferModelType(String modelId) {
        var normalized = modelId != null ? modelId.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("embed")) {
            return ModelType.EMBEDDING;
        }
        return ModelType.LANGUAGE;
    }

    protected Set<ModelFeature> inferFeatures(ModelType modelType, String modelId) {
        if (modelType != ModelType.LANGUAGE) {
            return Set.of();
        }
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
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
            if (item instanceof Map<?, ?> node) {
                var modelId = stringValue(node, idField);
                if (!modelId.isBlank()) {
                    var model = mapper.apply(node);
                    if (model != null) {
                        models.add(model);
                    }
                }
            }
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
        if (value == null || expected == null) {
            return false;
        }
        var normalizedExpected = expected.toLowerCase(Locale.ROOT);
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> containsToken(item, normalizedExpected));
        }
        var normalized = value.toString().toLowerCase(Locale.ROOT);
        return normalized.equals(normalizedExpected)
            || normalized.contains(normalizedExpected);
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
}
