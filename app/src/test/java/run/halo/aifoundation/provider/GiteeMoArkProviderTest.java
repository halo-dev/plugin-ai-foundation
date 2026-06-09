package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatModel;
import run.halo.app.extension.Metadata;

class GiteeMoArkProviderTest {

    private final GiteeMoArkProvider providerType = new GiteeMoArkProvider();

    @Test
    void metadata_matchesGiteeMoArkProvider() {
        assertThat(providerType.getProviderType()).isEqualTo("gitee-moark");
        assertThat(providerType.getDisplayName()).isEqualTo("Gitee 模力方舟");
        assertThat(providerType.getDescription()).isNotBlank();
        assertThat(providerType.getIconUrl())
            .isEqualTo("/plugins/ai-foundation/assets/static/brands/gitee-moark.png");
        assertThat(providerType.getWebsiteUrl()).isEqualTo("https://ai.gitee.com/");
        assertThat(providerType.getDocumentationUrl())
            .isEqualTo("https://ai.gitee.com/docs/products/apis/texts/text-generation");
        assertThat(providerType.isBuiltIn()).isTrue();
        assertThat(providerType.requiresBaseUrl()).isFalse();
        assertThat(providerType.getDefaultBaseUrl()).isEqualTo("https://ai.gitee.com/v1");
    }

    @Test
    void supportsOnlyOpenAiChatAdapter() {
        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(AdapterType.OPENAI_CHAT);
        assertThat(providerType.maxEmbeddingsPerCall()).isZero();
        assertThat(providerType.supportsParallelCalls()).isFalse();
        assertThat(providerType.buildEmbeddingModel(provider(null), "sk-test",
            "Qwen2.5-72B-Instruct")).isNull();
    }

    @Test
    void iconAssetIsPackagedWithMainResources() {
        assertThat(getClass().getResource("/static/brands/gitee-moark.png")).isNotNull();
    }

    @Test
    void buildChatModel_returnsOpenAiCompatibleChatModel() {
        var chatModel = providerType.buildChatModel(provider(null), "sk-test",
            "Qwen2.5-72B-Instruct");

        assertThat(chatModel).isInstanceOf(OpenAiCompatibleChatModel.class);
    }

    @Test
    void resolveBaseUrl_usesCustomBaseUrlWhenProvided() {
        assertThat(providerType.resolveBaseUrl(provider("https://example.com")))
            .isEqualTo("https://example.com");
    }

    @Test
    void discoverModels_usesDefaultOpenAiCompatibleModelsEndpoint() throws Exception {
        var request = new CompletableFuture<RequestCapture>();
        var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var serverThread = Thread.ofVirtual().start(() -> handleModelsRequest(server, request));

        try {
            var baseUrl = "http://127.0.0.1:" + server.getLocalPort() + "/v1";
            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(1);
                    var model = models.getFirst();
                    assertThat(model.modelId()).isEqualTo("Qwen2.5-72B-Instruct");
                    assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(model.features()).containsExactly(ModelFeature.STREAMING);
                    assertThat(model.adapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
                })
                .verifyComplete();

            assertThat(request).isCompletedWithValue(new RequestCapture(
                "GET /v1/models HTTP/1.1", "Bearer sk-test"));
        } finally {
            server.close();
            serverThread.join();
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("gitee-moark-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("gitee-moark");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private void handleModelsRequest(ServerSocket serverSocket,
        CompletableFuture<RequestCapture> request) {
        try (var socket = serverSocket.accept();
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.US_ASCII));
            var output = socket.getOutputStream()) {
            var requestLine = reader.readLine();
            var authorization = readAuthorizationHeader(reader);
            var body = "{\"data\":[{\"id\":\"Qwen2.5-72B-Instruct\"}]}";
            var response = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n"
                + body;
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
            request.complete(new RequestCapture(requestLine, authorization));
        } catch (Exception e) {
            request.completeExceptionally(e);
        }
    }

    private String readAuthorizationHeader(BufferedReader reader) throws java.io.IOException {
        String authorization = null;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.regionMatches(true, 0, "Authorization:", 0, "Authorization:".length())) {
                authorization = line.substring("Authorization:".length()).trim();
            }
        }
        return authorization;
    }

    private record RequestCapture(String requestLine, String authorization) {
    }
}
