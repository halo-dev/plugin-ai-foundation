package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;
import run.halo.app.extension.Metadata;

class FallbackModelDiscoveryTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("providersUsingModelsFallback")
    void identifierOnlyCatalogUsesProviderContractWithoutInspectingModelId(
        AiProviderType providerType, String basePath) throws Exception {
        var authorization = new AtomicReference<String>();
        var requestPath = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext(basePath + "/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestPath.set(exchange.getRequestURI().getPath());
                var body = """
                    {"object":"list","data":[
                      {"id":"opaque-embedding-rerank-model","object":"model",
                       "owned_by":"provider"}]}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();

        try {
            var provider = provider(providerType,
                "http://127.0.0.1:" + server.getAddress().getPort() + basePath);
            StepVerifier.create(providerType.discoverModels(provider, "test-key"))
                .assertNext(models -> assertThat(models).singleElement().satisfies(model -> {
                    assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(model.adapterType())
                        .isEqualTo(providerType.recommendAdapterType(ModelType.LANGUAGE).orElseThrow());
                    assertThat(model.source()).isEqualTo(DiscoverySource.RULE);
                    assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.LOW);
                    assertThat(model.features()).containsExactlyInAnyOrderElementsOf(
                        providerType.getSupportedFeatures(model.adapterType()));
                }))
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(requestPath.get()).isEqualTo(basePath + "/models");
        } finally {
            server.stop(0);
        }
    }

    static Stream<Arguments> providersUsingModelsFallback() {
        return Stream.of(
            Arguments.of(new OpenAiLikeProvider(), "/v1"),
            Arguments.of(new DouBaoProvider(), "/api/v3"),
            Arguments.of(new ZhiPuProvider(), "/api/paas/v4")
        );
    }

    private AiProvider provider(AiProviderType providerType, String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(providerType.getProviderType() + "-test");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType.getProviderType());
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
