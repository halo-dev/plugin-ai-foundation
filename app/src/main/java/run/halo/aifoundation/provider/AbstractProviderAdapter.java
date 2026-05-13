package run.halo.aifoundation.provider;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.aifoundation.extension.AiProvider;

@Slf4j
@Getter
public abstract class AbstractProviderAdapter implements ProviderAdapter {

    protected final AiProvider provider;
    protected final String apiKey;

    protected AbstractProviderAdapter(AiProvider provider, String apiKey) {
        this.provider = provider;
        this.apiKey = apiKey;
    }

    protected String resolveBaseUrl(String defaultBaseUrl) {
        var spec = provider.getSpec();
        var baseUrl = spec.getBaseUrl();
        return (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : defaultBaseUrl;
    }

    protected WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                HttpClient.create()
                    .responseTimeout(Duration.ofMinutes(5))
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            ));
    }

    protected RestClient.Builder restClientBuilder() {
        return RestClient.builder()
            .requestFactory(new ReactorClientHttpRequestFactory(
                HttpClient.create()
                    .responseTimeout(Duration.ofMinutes(5))
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            ));
    }

    @Override
    public org.springframework.ai.embedding.EmbeddingModel buildEmbeddingModel(String modelId) {
        return null;
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 96;
    }

    @Override
    public boolean supportsParallelCalls() {
        return true;
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels() {
        var baseUrl = resolveBaseUrl(getDefaultBaseUrl());
        var providerName = provider.getMetadata().getName();
        log.info("Discovering models for provider {}: type={}, baseUrl={}",
            providerName, getProviderType(), baseUrl);

        var wc = webClientBuilder().baseUrl(baseUrl).build();
        var requestSpec = wc.get().uri("/v1/models");

        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        customizeDiscoveryRequest(requestSpec);

        return requestSpec
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(json -> {
                var data = json.get("data");
                if (!(data instanceof List<?> dataList)) {
                    log.warn("Provider API response missing 'data' array for {}", providerName);
                    return Mono.just(List.<DiscoveredModel>of());
                }
                List<DiscoveredModel> models = new ArrayList<>();
                for (var item : dataList) {
                    if (item instanceof Map<?, ?> node) {
                        var modelIdObj = node.get("id");
                        var modelId = modelIdObj != null ? modelIdObj.toString() : "";
                        if (!modelId.isBlank()) {
                            models.add(new DiscoveredModel(
                                modelId, modelId, inferCapabilities(modelId)));
                        }
                    }
                }
                log.info("Discovered {} models for provider {}", models.size(), providerName);
                return Mono.just(models);
            });
    }

    protected Set<ModelCapability> inferCapabilities(String modelId) {
        var caps = new LinkedHashSet<ModelCapability>();
        if (modelId.toLowerCase().contains("embed")) {
            caps.add(ModelCapability.EMBEDDING);
        } else {
            caps.add(ModelCapability.CHAT);
        }
        return caps;
    }

    protected void customizeDiscoveryRequest(
        WebClient.RequestHeadersSpec<?> requestSpec) {
        // Default no-op; subclasses can override to add provider-specific headers
    }

    protected abstract String getDefaultBaseUrl();
}
