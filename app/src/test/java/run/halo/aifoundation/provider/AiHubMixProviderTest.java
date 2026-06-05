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

class AiHubMixProviderTest {

    private final AiHubMixProvider providerType = new AiHubMixProvider();

    @Test
    void discoverModels_mapsRemoteTypeAndFeatureFields() throws Exception {
        var capture = new RequestCapture();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v1/models", exchange -> handleModelsRequest(exchange, capture));
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);
                    var chat = models.get(0);
                    assertThat(chat.modelId()).isEqualTo("gpt-4o");
                    assertThat(chat.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(chat.adapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
                    assertThat(chat.features()).containsExactlyInAnyOrder(
                        ModelFeature.STREAMING,
                        ModelFeature.VISION,
                        ModelFeature.TOOL_CALL,
                        ModelFeature.REASONING
                    );
                    assertThat(chat.source()).isEqualTo(DiscoverySource.REMOTE);
                    assertThat(chat.confidence()).isEqualTo(DiscoveryConfidence.HIGH);

                    var embedding = models.get(1);
                    assertThat(embedding.modelId()).isEqualTo("text-embedding-3-small");
                    assertThat(embedding.modelType()).isEqualTo(ModelType.EMBEDDING);
                    assertThat(embedding.adapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
                    assertThat(embedding.features()).isEmpty();
                    assertThat(embedding.source()).isEqualTo(DiscoverySource.REMOTE);
                    assertThat(embedding.confidence()).isEqualTo(DiscoveryConfidence.HIGH);
                })
                .verifyComplete();

            assertThat(capture.requestLine).isEqualTo("GET /api/v1/models HTTP/1.1");
            assertThat(capture.authorization).isEqualTo("Bearer sk-test");
            assertThat(capture.appCode).isEqualTo("NEUE3459");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void discoverModels_allowsLargeModelCatalogResponses() throws Exception {
        var capture = new RequestCapture();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v1/models",
            exchange -> handleLargeModelsRequest(exchange, capture));
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).singleElement()
                        .satisfies(model -> {
                            assertThat(model.modelId()).isEqualTo("huge-catalog-chat");
                            assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                            assertThat(model.source()).isEqualTo(DiscoverySource.REMOTE);
                        });
                })
                .verifyComplete();

            assertThat(capture.requestLine).isEqualTo("GET /api/v1/models HTTP/1.1");
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
            capture.appCode = exchange.getRequestHeaders().getFirst("APP-Code");
            var body = """
                {
                  "data": [
                    {
                      "model_id": "gpt-4o",
                      "types": ["chat"],
                      "features": ["function_calling", "reasoning"],
                      "input_modalities": "text,image"
                    },
                    {
                      "model_id": "text-embedding-3-small",
                      "types": ["embedding"],
                      "features": []
                    },
                    {
                      "model_id": "image-only",
                      "types": ["image"]
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

    private void handleLargeModelsRequest(HttpExchange exchange, RequestCapture capture)
        throws IOException {
        try (exchange) {
            capture.requestLine = exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " HTTP/1.1";
            capture.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            capture.appCode = exchange.getRequestHeaders().getFirst("APP-Code");
            var body = """
                {
                  "data": [
                    {
                      "model_id": "huge-catalog-chat",
                      "types": ["chat"],
                      "features": [],
                      "desc": "%s"
                    }
                  ]
                }
                """.formatted("x".repeat(300_000));
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("aihubmix-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("aihubmix");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private static class RequestCapture {
        String requestLine;
        String authorization;
        String appCode;
    }
}
