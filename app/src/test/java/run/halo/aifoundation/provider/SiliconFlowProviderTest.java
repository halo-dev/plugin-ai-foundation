package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class SiliconFlowProviderTest {

    private final SiliconFlowProvider providerType = new SiliconFlowProvider();

    @Test
    void discoverModels_queriesTypedSubTypesAndNormalizesProfiles() throws Exception {
        var requests = new CopyOnWriteArrayList<RequestCapture>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> handleModelsRequest(exchange, requests));
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);
                    assertThat(models.get(0).modelId()).isEqualTo("Qwen/Qwen2.5-7B-Instruct");
                    assertThat(models.get(0).modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(models.get(0).features()).containsExactly(ModelFeature.STREAMING);
                    assertThat(models.get(0).adapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
                    assertThat(models.get(0).source()).isEqualTo(DiscoverySource.REMOTE);
                    assertThat(models.get(0).confidence()).isEqualTo(DiscoveryConfidence.HIGH);

                    assertThat(models.get(1).modelId()).isEqualTo("BAAI/bge-m3");
                    assertThat(models.get(1).modelType()).isEqualTo(ModelType.EMBEDDING);
                    assertThat(models.get(1).features()).isEmpty();
                    assertThat(models.get(1).adapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
                    assertThat(models.get(1).source()).isEqualTo(DiscoverySource.REMOTE);
                    assertThat(models.get(1).confidence()).isEqualTo(DiscoveryConfidence.HIGH);
                })
                .verifyComplete();

            assertThat(requests)
                .containsExactlyInAnyOrder(
                    new RequestCapture("sub_type=chat", "Bearer sk-test"),
                    new RequestCapture("sub_type=embedding", "Bearer sk-test")
                );
        } finally {
            server.stop(0);
        }
    }

    private void handleModelsRequest(HttpExchange exchange, List<RequestCapture> requests)
        throws IOException {
        try (exchange) {
            var query = exchange.getRequestURI().getRawQuery();
            var authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requests.add(new RequestCapture(query, authorization));

            var body = switch (query) {
                case "sub_type=chat" -> "{\"data\":[{\"id\":\"Qwen/Qwen2.5-7B-Instruct\"}]}";
                case "sub_type=embedding" -> "{\"data\":[{\"id\":\"BAAI/bge-m3\"}]}";
                default -> "{\"data\":[]}";
            };
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("siliconflow-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("siliconflow");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private record RequestCapture(String query, String authorization) {
    }
}
