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
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class OllamaProviderTest {

    private final OllamaProvider providerType = new OllamaProvider();

    @Test
    void metadata_includesEmbeddingAdapter() {
        assertThat(providerType.getSupportedAdapterTypes())
            .containsExactly(AdapterType.OLLAMA_CHAT, AdapterType.OLLAMA_EMBEDDING);
        assertThat(providerType.getSupportedModelTypes())
            .containsExactly(ModelType.LANGUAGE, ModelType.EMBEDDING);
    }

    @Test
    void discoverModels_keepsTagInferenceLowConfidence() throws Exception {
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/tags", this::handleTagsRequest);
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

            StepVerifier.create(providerType.discoverModels(provider(baseUrl), ""))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);
                    assertThat(models.get(0).modelId()).isEqualTo("llama3");
                    assertThat(models.get(0).modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(models.get(0).adapterType()).isEqualTo(AdapterType.OLLAMA_CHAT);
                    assertThat(models.get(0).source()).isEqualTo(DiscoverySource.RULE);
                    assertThat(models.get(0).confidence()).isEqualTo(DiscoveryConfidence.LOW);

                    assertThat(models.get(1).modelId()).isEqualTo("nomic-embed-text");
                    assertThat(models.get(1).modelType()).isEqualTo(ModelType.EMBEDDING);
                    assertThat(models.get(1).adapterType()).isEqualTo(AdapterType.OLLAMA_EMBEDDING);
                    assertThat(models.get(1).source()).isEqualTo(DiscoverySource.RULE);
                    assertThat(models.get(1).confidence()).isEqualTo(DiscoveryConfidence.LOW);
                })
                .verifyComplete();
        } finally {
            server.stop(0);
        }
    }

    private void handleTagsRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            var body = """
                {
                  "models": [
                    {"name": "llama3"},
                    {"name": "nomic-embed-text"}
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
        metadata.setName("ollama-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("ollama");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
