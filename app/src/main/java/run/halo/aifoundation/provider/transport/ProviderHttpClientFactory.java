package run.halo.aifoundation.provider.transport;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider.Proxy;
import run.halo.aifoundation.extension.AiProvider;

/**
 * Creates provider-neutral HTTP clients with the plugin's connection policy.
 */
public final class ProviderHttpClientFactory {

    public static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);
    public static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    public static final int DISCOVERY_MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024;

    private ProviderHttpClientFactory() {
    }

    public static HttpClient httpClient(AiProvider provider) {
        var client = HttpClient.create()
            .responseTimeout(RESPONSE_TIMEOUT)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS);
        var proxy = proxy(provider);
        if (proxy == null) {
            return client;
        }
        return client.proxy(builder -> builder
            .type(Proxy.HTTP)
            .host(proxy.host())
            .port(proxy.port()));
    }

    public static WebClient.Builder webClientBuilder(AiProvider provider) {
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient(provider)));
    }

    public static WebClient.Builder discoveryWebClientBuilder(AiProvider provider) {
        return webClientBuilder(provider)
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                    .maxInMemorySize(DISCOVERY_MAX_IN_MEMORY_SIZE))
                .build());
    }

    public static RestClient.Builder restClientBuilder(AiProvider provider) {
        return RestClient.builder()
            .requestFactory(new ReactorClientHttpRequestFactory(httpClient(provider)));
    }

    private static ProxySettings proxy(AiProvider provider) {
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
        var port = spec.getProxyPort();
        if (port < 1) {
            throw new IllegalArgumentException("proxyPort must be between 1 and 65535");
        }
        if (port > 65_535) {
            throw new IllegalArgumentException("proxyPort must be between 1 and 65535");
        }
        return new ProxySettings(spec.getProxyHost().trim(), port);
    }

    private record ProxySettings(String host, int port) {
    }
}
