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
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
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
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

@Slf4j
public abstract class AbstractAiProviderType implements AiProviderType {

    private static final int DISCOVERY_MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024;

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

        return getDiscoveryJson(provider, apiKey, uriBuilder -> uriBuilder.path("/v1/models")
                .build(), this::customizeDiscoveryRequest)
            .map(json -> {
                var dataList = listValue(json, "data");
                if (dataList == null) {
                    log.warn("Provider API response missing 'data' array for {}", providerName);
                    return List.<DiscoveredModel>of();
                }
                var models = discoveredModelsFromNodes(dataList, "id",
                    node -> inferModelProfile(stringValue(node, "id")));
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
}
