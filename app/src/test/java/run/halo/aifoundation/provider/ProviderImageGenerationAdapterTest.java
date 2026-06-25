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
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.image.DashScopeImageGenerationClient;
import run.halo.aifoundation.provider.support.image.MiniMaxImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ModelArkImageGenerationClient;
import run.halo.aifoundation.provider.support.image.OpenRouterImageGenerationClient;
import run.halo.aifoundation.provider.support.image.SiliconFlowImageGenerationClient;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleImageGenerationClient;
import run.halo.app.extension.Metadata;

class ProviderImageGenerationAdapterTest {

    @Test
    void providersBuildConfiguredImageGenerationClients() {
        assertImageClient(new OpenAiProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new OpenAiLikeProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new AiHubMixProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new ErnieProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new GiteeMoArkProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new ZhiPuProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new OllamaProvider(), AdapterType.OPENAI_IMAGE,
            OpenAiCompatibleImageGenerationClient.class);
        assertImageClient(new OpenRouterProvider(), AdapterType.OPENROUTER_IMAGE,
            OpenRouterImageGenerationClient.class);
        assertImageClient(new DashScopeProvider(), AdapterType.DASHSCOPE_IMAGE,
            DashScopeImageGenerationClient.class);
        assertImageClient(new DouBaoProvider(), AdapterType.DOUBAO_IMAGE,
            ModelArkImageGenerationClient.class);
        assertImageClient(new MiniMaxProvider(), AdapterType.MINIMAX_IMAGE,
            MiniMaxImageGenerationClient.class);
        assertImageClient(new SiliconFlowProvider(), AdapterType.SILICONFLOW_IMAGE,
            SiliconFlowImageGenerationClient.class);
    }

    @Test
    void openRouterDiscoverModels_mapsImageModelsEndpointToImageAdapter() throws Exception {
        var requests = new CopyOnWriteArrayList<RequestCapture>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> handleOpenRouterModels(exchange, requests));
        server.createContext("/v1/images/models",
            exchange -> handleOpenRouterImageModels(exchange, requests));
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            StepVerifier.create(new OpenRouterProvider().discoverModels(
                    provider("openrouter", baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(3);
                    assertThat(models.get(0).modelId()).isEqualTo("openrouter-chat");
                    assertThat(models.get(0).modelType()).isEqualTo(ModelType.LANGUAGE);

                    var imageToImage = models.get(1);
                    assertThat(imageToImage.modelId()).isEqualTo("openrouter-image-edit");
                    assertImageDiscovery(imageToImage);
                    assertThat(imageToImage.capabilities()
                        .getImageGeneration()
                        .getTextToImage()).isTrue();
                    assertThat(imageToImage.capabilities()
                        .getImageGeneration()
                        .getImageToImage()).isTrue();

                    var textToImage = models.get(2);
                    assertThat(textToImage.modelId()).isEqualTo("openrouter-image-create");
                    assertImageDiscovery(textToImage);
                    assertThat(textToImage.capabilities()
                        .getImageGeneration()
                        .getTextToImage()).isTrue();
                    assertThat(textToImage.capabilities()
                        .getImageGeneration()
                        .getImageToImage()).isNull();
                })
                .verifyComplete();

            assertThat(requests)
                .containsExactlyInAnyOrder(
                    new RequestCapture("/v1/models", "Bearer sk-test"),
                    new RequestCapture("/v1/images/models", "Bearer sk-test")
                );
        } finally {
            server.stop(0);
        }
    }

    private void assertImageClient(AiProviderType providerType, AdapterType adapterType,
        Class<?> clientType) {
        assertThat(providerType.getSupportedAdapterTypes()).contains(adapterType);
        assertThat(providerType.buildImageGenerationClient(
            provider(providerType.getProviderType(), "http://localhost/v1"), "sk-test",
            "image-model")).isInstanceOf(clientType);
    }

    private void assertImageDiscovery(DiscoveredModel model) {
        assertThat(model.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
        assertThat(model.adapterType()).isEqualTo(AdapterType.OPENROUTER_IMAGE);
        assertThat(model.source()).isEqualTo(DiscoverySource.REMOTE);
        assertThat(model.capabilities().getImageGeneration().getMaxImagesPerCall())
            .isEqualTo(1);
        assertThat(model.capabilitySources().getImageGeneration())
            .isEqualTo(CapabilitySource.REMOTE);
    }

    private void handleOpenRouterModels(HttpExchange exchange, List<RequestCapture> requests)
        throws IOException {
        respond(exchange, requests, """
            {
              "data": [
                {
                  "id": "openrouter-chat",
                  "architecture": {
                    "input_modalities": ["text"],
                    "output_modalities": ["text"]
                  }
                }
              ]
            }
            """);
    }

    private void handleOpenRouterImageModels(HttpExchange exchange, List<RequestCapture> requests)
        throws IOException {
        respond(exchange, requests, """
            {
              "data": [
                {
                  "id": "openrouter-image-edit",
                  "name": "OpenRouter Image Edit",
                  "architecture": {
                    "input_modalities": ["text", "image"],
                    "output_modalities": ["image"]
                  }
                },
                {
                  "id": "openrouter-image-create",
                  "name": "OpenRouter Image Create",
                  "architecture": {
                    "input_modalities": ["text"],
                    "output_modalities": ["image"]
                  }
                }
              ]
            }
            """);
    }

    private void respond(HttpExchange exchange, List<RequestCapture> requests, String body)
        throws IOException {
        try (exchange) {
            requests.add(new RequestCapture(exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization")));
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private AiProvider provider(String providerType, String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(providerType + "-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private record RequestCapture(String path, String authorization) {
    }
}
