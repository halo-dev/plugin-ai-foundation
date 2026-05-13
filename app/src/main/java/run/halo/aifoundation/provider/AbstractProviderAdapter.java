package run.halo.aifoundation.provider;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.Getter;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import run.halo.aifoundation.extension.AiProvider;

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
}
