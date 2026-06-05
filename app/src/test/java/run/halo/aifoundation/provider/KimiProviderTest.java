package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class KimiProviderTest {

    private final KimiProvider providerType = new KimiProvider();

    @Test
    void discoverModels_mapsRemoteCapabilityFlags() throws Exception {
        var capture = new RequestCapture();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> handleModelsRequest(exchange, capture));
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);
                    var vision = models.get(0);
                    assertThat(vision.modelId()).isEqualTo("kimi-vision");
                    assertThat(vision.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(vision.adapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
                    assertThat(vision.features()).containsExactlyInAnyOrder(
                        ModelFeature.STREAMING,
                        ModelFeature.VISION,
                        ModelFeature.REASONING
                    );
                    assertThat(vision.source()).isEqualTo(DiscoverySource.REMOTE);
                    assertThat(vision.confidence()).isEqualTo(DiscoveryConfidence.HIGH);

                    var text = models.get(1);
                    assertThat(text.modelId()).isEqualTo("kimi-text");
                    assertThat(text.features()).containsExactly(ModelFeature.STREAMING);
                })
                .verifyComplete();

            assertThat(capture.requestLine).isEqualTo("GET /v1/models HTTP/1.1");
            assertThat(capture.authorization).isEqualTo("Bearer sk-test");
        } finally {
            server.stop(0);
        }
    }

    private void handleModelsRequest(HttpExchange exchange, RequestCapture capture)
        throws IOException {
        try (exchange) {
            capture.requestLine = exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " HTTP/1.1";
            capture.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            var body = """
                {
                  "data": [
                    {
                      "id": "kimi-vision",
                      "supports_image_in": true,
                      "supports_reasoning": true
                    },
                    {
                      "id": "kimi-text",
                      "supports_image_in": false,
                      "supports_reasoning": false
                    }
                  ]
                }
                """;
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("kimi-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("kimi");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private static class RequestCapture {
        String requestLine;
        String authorization;
    }
}
