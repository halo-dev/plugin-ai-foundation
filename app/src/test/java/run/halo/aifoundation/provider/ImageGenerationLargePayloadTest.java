package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.app.extension.Metadata;

/**
 * End-to-end regression for image responses that inline tens of megabytes of base64 data:
 * both the transport buffer and the Jackson parser must accept them.
 */
class ImageGenerationLargePayloadTest {

    private static final int PAYLOAD_LENGTH = 25 * 1024 * 1024;

    @Test
    void openAiClientHandlesResponseLargerThanJacksonDefaultStringLimit() throws Exception {
        withPayloadServer(server -> {
            var client = new OpenAiProvider()
                .buildImageGenerationClient(provider("openai", server), "sk-test", "gpt-image-1");
            StepVerifier.create(client.generateImage(
                    GenerateImageRequest.builder().prompt("draw").build()))
                .assertNext(result -> assertThat(result.getImages().getFirst().getBase64())
                    .hasSize(PAYLOAD_LENGTH))
                .verifyComplete();
        });
    }

    @Test
    void openAiCompatibleClientHandlesResponseLargerThanJacksonDefaultStringLimit()
        throws Exception {
        withPayloadServer(server -> {
            var client = new OpenAiLikeProvider()
                .buildImageGenerationClient(provider("openailike", server), "sk-test",
                    "image-model");
            StepVerifier.create(client.generateImage(
                    GenerateImageRequest.builder().prompt("draw").build()))
                .assertNext(result -> assertThat(result.getImages().getFirst().getBase64())
                    .hasSize(PAYLOAD_LENGTH))
                .verifyComplete();
        });
    }

    private static void withPayloadServer(ServerConsumer consumer) throws Exception {
        var payload = "{\"data\":[{\"b64_json\":\"" + "a".repeat(PAYLOAD_LENGTH) + "\"}]}";
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            try (exchange) {
                var bytes = payload.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            consumer.accept(server);
        } finally {
            server.stop(0);
        }
    }

    private static AiProvider provider(String providerType, HttpServer server) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(providerType + "-large-payload-test");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setDisplayName(providerType);
        spec.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        spec.setImageEndpointPath("/images/generations");
        provider.setSpec(spec);
        return provider;
    }

    @FunctionalInterface
    private interface ServerConsumer {
        void accept(HttpServer server);
    }
}
