package run.halo.aifoundation.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

@ProviderContractSource(
    provider = "openai",
    officialDocumentation = "https://platform.openai.com/docs/api-reference/images/createEdit",
    retrievedAt = "2026-08-25"
)
class OpenAiImageGenerationClientTest {

    @Test
    void editsImagesWithTheDocumentedMultipartEndpoint() throws Exception {
        var requestBody = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/images/edits", exchange -> {
            try (exchange) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.ISO_8859_1));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var response = """
                    {"data":[{"b64_json":"ZWRpdGVk"}],
                     "usage":{"input_tokens":5,"output_tokens":7,"total_tokens":12}}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            var client = client(baseUrl);
            var request = GenerateImageRequest.builder()
                .prompt("Keep the subject and change the background")
                .images(List.of(
                    DataContent.data(new byte[] {1, 2, 3}, "image/png", "subject.png"),
                    DataContent.data(new byte[] {4, 5, 6}, "image/webp")))
                .mask(DataContent.data(new byte[] {7, 8, 9}, "image/png", "mask.png"))
                .build();

            StepVerifier.create(client.generateImage(request, null,
                    Map.of("input_fidelity", "high")))
                .assertNext(result -> {
                    assertThat(result.getImage().getBase64()).isEqualTo("ZWRpdGVk");
                    assertThat(result.getUsage().getTotalTokens()).isEqualTo(12);
                })
                .verifyComplete();

            assertThat(contentType.get()).startsWith("multipart/form-data;boundary=");
            assertThat(authorization.get()).isEqualTo("Bearer secret");
            assertThat(requestBody.get())
                .contains("name=\"model\"")
                .contains("name=\"prompt\"")
                .contains("name=\"input_fidelity\"")
                .contains("name=\"image\"; filename=\"subject.png\"")
                .contains("name=\"image\"; filename=\"image-2.webp\"")
                .contains("name=\"mask\"; filename=\"mask.png\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void editUploadDoesNotDownloadUrlInputs() {
        var client = client("https://api.openai.com/v1");
        var request = GenerateImageRequest.builder()
            .prompt("Edit")
            .images(List.of(DataContent.url("https://example.com/image.png", "image/png")))
            .build();

        assertThatThrownBy(() -> client.generateImage(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("caller-provided image data");
    }

    private OpenAiImageGenerationClient client(String baseUrl) {
        return new OpenAiImageGenerationClient(new ImageGenerationClientOptions(
            "openai", baseUrl, "secret", "future-image-model", null), WebClient.builder());
    }
}
