package run.halo.aifoundation.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

@ProviderContractSource(
    provider = "ollama",
    officialDocumentation = "https://docs.ollama.com/api/embed; "
        + "https://docs.ollama.com/api/openai-compatibility",
    retrievedAt = "2026-08-25"
)
class OllamaDomainClientsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void embeddingOptionsExposeDocumentedNativeControls() {
        var warnings = new ArrayList<run.halo.aifoundation.embedding.EmbeddingWarning>();
        var request = EmbeddingRequest.builder().dimensions(384).build();
        var nativeOptions = Map.<String, Object>of(
            "truncate", false,
            "options", Map.of("num_gpu", 1, "num_thread", 8, "use_mmap", true));

        var options = (OllamaEmbeddingOptions) OllamaEmbeddingOptionsFactory.build(request,
            new EmbeddingModelProviderOptions(OllamaEmbeddingOptionsFactory::build)
                .withNativeOptions(nativeOptions),
            warnings);

        assertThat(options.getDimensions()).isEqualTo(384);
        assertThat(options.getTruncate()).isFalse();
        assertThat(options.getNumGPU()).isEqualTo(1);
        assertThat(options.getNumThread()).isEqualTo(8);
        assertThat(options.getUseMMap()).isTrue();
        assertThat(warnings).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void embeddingModelUsesNativeEmbedContract() throws Exception {
        var requestBody = new AtomicReference<Map<String, Object>>();
        var authorization = new AtomicReference<String>();
        var traceHeader = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/embed", exchange -> {
            try (exchange) {
                requestBody.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                traceHeader.set(exchange.getRequestHeaders().getFirst("X-Trace"));
                var bytes = """
                    {"model":"embeddinggemma","embeddings":[[0.1,0.2],[0.3,0.4]],
                     "prompt_eval_count":7,"total_duration":1000,"load_duration":200}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var defaults = new OllamaEmbeddingOptions(
                "embeddinggemma", null, null, Map.of());
            var model = new OllamaEmbeddingModel(baseUrl, "secret", defaults,
                WebClient.builder());
            var requested = new OllamaEmbeddingOptions(null, 384, false,
                Map.of("num_thread", 8));

            var response = model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("first", "second"), requested), Map.of("X-Trace", "trace-1"));

            assertThat(authorization.get()).isEqualTo("Bearer secret");
            assertThat(traceHeader.get()).isEqualTo("trace-1");
            assertThat(requestBody.get()).containsEntry("model", "embeddinggemma")
                .containsEntry("input", List.of("first", "second"))
                .containsEntry("dimensions", 384)
                .containsEntry("truncate", false)
                .doesNotContainKey("keep_alive");
            assertThat((Map<String, Object>) requestBody.get().get("options"))
                .containsEntry("num_thread", 8);
            assertThat(response.getResults()).extracting(result -> result.getOutput().length)
                .containsExactly(2, 2);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(7);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageClientOwnsExperimentalRequestAndResponseContract() throws Exception {
        var requestBody = new AtomicReference<Map<String, Object>>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            try (exchange) {
                requestBody.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var bytes = """
                    {"created":1787531200,"data":[{"b64_json":"aW1hZ2U="}],
                     "usage":{"input_tokens":12,"total_tokens":12}}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            var client = new OllamaImageGenerationClient(new ImageGenerationClientOptions(
                "ollama", baseUrl, "secret", "x/z-image-turbo", null), WebClient.builder());
            var request = GenerateImageRequest.builder().prompt("Halo mascot")
                .n(1).size("1024x1024").responseFormat(ImageResponseFormat.BASE64).build();

            StepVerifier.create(client.generateImage(request))
                .assertNext(result -> {
                    assertThat(result.getImages()).singleElement().satisfies(image -> {
                        assertThat(image.isBase64()).isTrue();
                        assertThat(image.getBase64()).isEqualTo("aW1hZ2U=");
                    });
                    assertThat(result.getUsage().getInputTokens()).isEqualTo(12);
                })
                .verifyComplete();

            assertThat(authorization.get()).isEqualTo("Bearer secret");
            assertThat(requestBody.get()).containsEntry("model", "x/z-image-turbo")
                .containsEntry("prompt", "Halo mascot")
                .containsEntry("n", 1)
                .containsEntry("size", "1024x1024")
                .containsEntry("response_format", "b64_json");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void imageClientRejectsUndocumentedUrlResponses() {
        var client = new OllamaImageGenerationClient(new ImageGenerationClientOptions(
            "ollama", "https://example.com/v1", "secret", "image-model", null),
            WebClient.builder());

        assertThatThrownBy(() -> client.requestBody(GenerateImageRequest.builder()
            .prompt("Halo mascot")
            .responseFormat(ImageResponseFormat.URL)
            .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BASE64");
    }

    @Test
    void imageClientForwardsConfiguredNativeOptionsWithoutOverridingPortableFields() {
        var client = new OllamaImageGenerationClient(new ImageGenerationClientOptions(
            "ollama", "https://example.com/v1", "secret", "configured-model", null),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Halo mascot")
            .size("1024x1024")
            .build();

        var body = client.requestBody(request, Map.of(
            "provider_extension", true,
            "model", "ignored-model",
            "size", "ignored-size"));

        assertThat(body)
            .containsEntry("provider_extension", true)
            .containsEntry("model", "configured-model")
            .containsEntry("size", "1024x1024");
    }
}
