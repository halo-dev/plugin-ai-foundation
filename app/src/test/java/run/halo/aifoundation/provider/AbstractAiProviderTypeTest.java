package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ChannelOption;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import reactor.netty.transport.ProxyProvider.Proxy;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class AbstractAiProviderTypeTest {

    static class TestProviderType extends AbstractAiProviderType {
        @Override
        public String getProviderType() {
            return "test";
        }

        @Override
        public String getDisplayName() {
            return "Test";
        }

        @Override
        public boolean isBuiltIn() {
            return true;
        }

        @Override
        public boolean requiresBaseUrl() {
            return false;
        }

        @Override
        public String getDefaultBaseUrl() {
            return "https://test.example.com";
        }

        @Override
        public java.util.List<AdapterType> getSupportedAdapterTypes() {
            return java.util.List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING);
        }

        @Override
        public org.springframework.ai.chat.model.ChatModel buildChatModel(
            AiProvider provider, String apiKey, String modelId) {
            return null;
        }
    }

    @Test
    void resolveBaseUrl_usesDefaultWhenSpecBaseUrlIsNull() {
        var provider = providerWithBaseUrl(null);
        var type = new TestProviderType();
        assertThat(type.resolveBaseUrl(provider)).isEqualTo("https://test.example.com");
    }

    @Test
    void resolveBaseUrl_usesDefaultWhenSpecBaseUrlIsBlank() {
        var provider = providerWithBaseUrl("  ");
        var type = new TestProviderType();
        assertThat(type.resolveBaseUrl(provider)).isEqualTo("https://test.example.com");
    }

    @Test
    void resolveBaseUrl_usesSpecBaseUrlWhenProvided() {
        var provider = providerWithBaseUrl("https://custom.example.com");
        var type = new TestProviderType();
        assertThat(type.resolveBaseUrl(provider)).isEqualTo("https://custom.example.com");
    }

    @Test
    void defaults_maxEmbeddingsPerCall_is96() {
        assertThat(new TestProviderType().maxEmbeddingsPerCall()).isEqualTo(96);
    }

    @Test
    void defaults_supportsParallelCalls_isTrue() {
        assertThat(new TestProviderType().supportsParallelCalls()).isTrue();
    }

    @Test
    void defaults_buildEmbeddingModel_returnsNull() {
        var provider = providerWithBaseUrl(null);
        assertThat(new TestProviderType().buildEmbeddingModel(provider, "key", "model")).isNull();
    }

    @Test
    void httpClient_withoutProxy_keepsBaseTimeoutConfiguration() {
        var client = new TestProviderType().httpClient(providerWithBaseUrl(null));

        assertThat(client.configuration().proxyProvider()).isNull();
        assertThat(client.configuration().responseTimeout()).isEqualTo(java.time.Duration.ofMinutes(5));
        assertThat(client.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS))
            .isEqualTo(10_000);
    }

    @Test
    void httpClient_withProxy_configuresHttpProxy() {
        var provider = providerWithProxy("proxy.example.com", 8080);
        var client = new TestProviderType().httpClient(provider);

        var proxy = client.configuration().proxyProviderSupplier().get();
        assertThat(proxy).isNotNull();
        assertThat(proxy.getType()).isEqualTo(Proxy.HTTP);
        assertThat(proxy.getAddress().get())
            .extracting(InetSocketAddress::getHostString, InetSocketAddress::getPort)
            .containsExactly("proxy.example.com", 8080);
        assertThat(client.configuration().responseTimeout()).isEqualTo(java.time.Duration.ofMinutes(5));
        assertThat(client.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS))
            .isEqualTo(10_000);
    }

    @Test
    void discoverModels_withProxyRoutesRequestThroughProxy() throws Exception {
        var proxyHit = new CompletableFuture<Boolean>();
        var proxyServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var proxyThread = Thread.ofVirtual().start(() -> handleSingleProxyRequest(proxyServer, proxyHit));

        try {
            var provider = providerWithProxy("127.0.0.1", proxyServer.getLocalPort());
            provider.getSpec().setBaseUrl("http://upstream.example.test");

            StepVerifier.create(new TestProviderType().discoverModels(provider, "key"))
                .assertNext(models -> {
                    assertThat(models).hasSize(1);
                    var model = models.getFirst();
                    assertThat(model.modelId()).isEqualTo("gpt-test");
                    assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(model.features()).containsExactly(ModelFeature.STREAMING);
                    assertThat(model.adapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
                    assertThat(model.source()).isEqualTo(DiscoverySource.RULE);
                    assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.LOW);
                })
                .verifyComplete();
            assertThat(proxyHit).isCompletedWithValue(true);
        } finally {
            proxyServer.close();
            proxyThread.join();
        }
    }

    @Test
    void inferModelProfile_detectsEmbeddingModel() {
        var profile = new TestProviderType().inferModelProfile("text-embedding-3-small");

        assertThat(profile.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(profile.adapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
        assertThat(profile.source()).isEqualTo(DiscoverySource.RULE);
        assertThat(profile.confidence()).isEqualTo(DiscoveryConfidence.LOW);
    }

    @Test
    void remoteDiscoveredModel_buildsHighConfidenceProfile() {
        var profile = new TestProviderType().remoteDiscoveredModel("remote-embedding",
            ModelType.EMBEDDING, Set.of(), AdapterType.OPENAI_EMBEDDING);

        assertThat(profile.modelId()).isEqualTo("remote-embedding");
        assertThat(profile.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(profile.features()).isEmpty();
        assertThat(profile.adapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
        assertThat(profile.source()).isEqualTo(DiscoverySource.REMOTE);
        assertThat(profile.confidence()).isEqualTo(DiscoveryConfidence.HIGH);
    }

    @Test
    void discoveredModelsFromNodes_skipsMalformedItems() {
        var type = new TestProviderType();
        var models = type.discoveredModelsFromNodes(List.of(
            Map.of("id", "gpt-test"),
            Map.of("name", "missing-id"),
            "not-object"
        ), "id", node -> type.inferModelProfile(type.stringValue(node, "id")));

        assertThat(models).singleElement()
            .extracting(DiscoveredModel::modelId)
            .isEqualTo("gpt-test");
    }

    @Test
    void discoverModels_returnsEmptyListWhenDataArrayIsMissing() throws Exception {
        var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var serverThread = Thread.ofVirtual().start(() -> handleSingleHttpRequest(server, "{}"));

        try {
            var provider = providerWithBaseUrl("http://127.0.0.1:" + server.getLocalPort());

            StepVerifier.create(new TestProviderType().discoverModels(provider, "key"))
                .expectNext(List.of())
                .verifyComplete();
        } finally {
            server.close();
            serverThread.join();
        }
    }

    private AiProvider providerWithBaseUrl(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("test-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private AiProvider providerWithProxy(String proxyHost, Integer proxyPort) {
        var provider = providerWithBaseUrl(null);
        provider.getSpec().setProxyHost(proxyHost);
        provider.getSpec().setProxyPort(proxyPort);
        return provider;
    }

    private void handleSingleProxyRequest(ServerSocket serverSocket, CompletableFuture<Boolean> proxyHit) {
        try (var socket = serverSocket.accept();
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.US_ASCII));
            var output = socket.getOutputStream()) {
            readHttpRequest(reader);
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII));
            output.flush();

            readHttpRequest(reader);
            var body = "{\"data\":[{\"id\":\"gpt-test\"}]}";
            var response = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n"
                + body;
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
            proxyHit.complete(true);
        } catch (Exception e) {
            proxyHit.completeExceptionally(e);
        }
    }

    private void readHttpRequest(BufferedReader reader) throws java.io.IOException {
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // Drain request headers.
        }
    }

    private void handleSingleHttpRequest(ServerSocket serverSocket, String body) {
        try (var socket = serverSocket.accept();
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.US_ASCII));
            var output = socket.getOutputStream()) {
            readHttpRequest(reader);
            var response = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n"
                + body;
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception ignored) {
        }
    }
}
