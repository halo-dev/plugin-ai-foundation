package run.halo.aifoundation.provider.tokenhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "tokenhub",
    officialDocumentation = "https://cloud.tencent.com/document/product/1823/135872",
    retrievedAt = "2026-10-16"
)
class TokenHubProviderTest {

    private final TokenHubProvider provider = new TokenHubProvider();
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void exposesChatOnlyPresetAndConservativeCapabilities() {
        assertThat(provider.isBuiltIn()).isTrue();
        assertThat(provider.requiresBaseUrl()).isFalse();
        assertThat(provider.getProviderType()).isEqualTo("tokenhub");
        assertThat(provider.getDisplayName()).isEqualTo("腾讯云 TokenHub");
        assertThat(provider.getDescription()).isNotBlank();
        assertThat(provider.getWebsiteUrl()).startsWith("https://");
        assertThat(provider.getDocumentationUrl()).startsWith("https://cloud.tencent.com/");
        assertThat(getClass().getResource("/static/brands/tokenhub.png")).isNotNull();
        assertThat(provider.getSupportedModelTypes()).containsExactly(ModelType.LANGUAGE);
        assertThat(provider.getSupportedAdapterTypes()).containsExactly(AdapterType.TOKENHUB_CHAT);
        assertThat(provider.getSupportedFeatures()).containsExactly(ModelFeature.STREAMING,
            ModelFeature.TOOL_CALL, ModelFeature.VISION);
        assertThat(provider.maxEmbeddingsPerCall()).isZero();
        assertThat(provider.buildEmbeddingModel(config(null), "test-key", "model")).isNull();
        assertThat(provider.languageModelProviderOptions().nativeStrictToolSchemas()).isFalse();
        assertThat(provider.languageModelProviderOptions().seedSupported()).isTrue();
        assertThat(provider.languageModelProviderOptions().structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.PROMPT_ONLY);
        var model = (TokenHubChatModel) provider.buildChatModel(config(null), "test-key", "model");
        assertThat(model.getOptions().getBaseUrl()).isEqualTo("https://tokenhub.tencentmaas.com/v1");
        assertThat(model.getOptions().getModel()).isEqualTo("model");
        assertThat(AdapterType.fromValue("tokenhub-chat")).isEqualTo(AdapterType.TOKENHUB_CHAT);
    }

    @Test
    void retainsDocumentedParameterMappings() {
        var mappings = provider.getDefaultParameterMappings(AdapterType.TOKENHUB_CHAT);
        assertThat(mappings.get(ModelParameter.SEED))
            .isEqualTo(DefaultParameterMapping.template("chat.seed"));
        assertThat(mappings.get(ModelParameter.PARALLEL_TOOL_CALLS))
            .isEqualTo(DefaultParameterMapping.template("chat.parallel-tool-calls"));
        assertThat(mappings.get(ModelParameter.TOP_K))
            .isEqualTo(DefaultParameterMapping.unsupported());
    }

    @Test
    void discoveryIsDisabledUntilReliableModalityMetadataExists() {
        StepVerifier.create(provider.discoverModels(config(baseUrl()), "test-key"))
            .assertNext(models -> assertThat(models).isEmpty())
            .verifyComplete();
    }

    @Test
    void sendsChatToConfiguredBaseUrlAndPreservesUsage() {
        var body = new AtomicReference<Map<?, ?>>();
        var auth = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new ObjectMapper().readValue(exchange.getRequestBody(), Map.class));
            respond(exchange, 200, "application/json", """
                {"id":"chat-1","object":"chat.completion","model":"configured-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},
                 "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                """);
        });
        var model = provider.buildChatModel(config(baseUrl()), "test-key", "configured-model");
        var request = GenerateTextRequest.builder().seed(17).parallelToolCalls(false).build();
        var options = provider.languageModelProviderOptions().chatOptionsFactory().build(request);
        var response = model.call(new Prompt("Hi", options));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(3);
        assertThat(body.get().get("model")).isEqualTo("configured-model");
        assertThat(body.get().get("stream")).isNotEqualTo(true);
        assertThat(auth.get()).isEqualTo("Bearer test-key");
        assertThat(body.get().get("seed")).isEqualTo(17);
        assertThat(body.get().get("parallel_tool_calls")).isEqualTo(false);
    }

    @Test
    void sendsImageInputForManuallyConfiguredVisionModel() {
        var body = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        server.createContext("/v1/chat/completions", exchange -> {
            body.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            respond(exchange, 200, "application/json", """
                {"id":"vision-1","choices":[{"index":0,"message":{
                "role":"assistant","content":"A landscape"},"finish_reason":"stop"}]}
                """);
        });
        var model = provider.buildChatModel(config(baseUrl()), "test-key", "configured-vision-model");
        var user = UserMessage.builder().text("Describe this image")
            .media(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(URI.create("https://example.com/landscape.png")).build())
            .build();
        var response = model.call(new Prompt(List.of(user)));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("A landscape");
        assertThat(body.get().path("model").asText()).isEqualTo("configured-vision-model");
        var content = body.get().path("messages").get(0).path("content");
        assertThat(content.get(0).path("type").asText()).isEqualTo("text");
        assertThat(content.get(0).path("text").asText()).isEqualTo("Describe this image");
        assertThat(content.get(1).path("type").asText()).isEqualTo("image_url");
        assertThat(content.get(1).path("image_url").path("url").asText())
            .isEqualTo("https://example.com/landscape.png");
    }

    @Test
    void supportsStandardSseStreaming() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200,
            "text/event-stream", """
                data: {"id":"chat-2","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}

                data: {"id":"chat-2","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """));
        var model = provider.buildChatModel(config(baseUrl()), "test-key", "configured-model");
        var chunks = model.stream(new Prompt("Hi")).collectList().block(Duration.ofSeconds(5));
        assertThat(chunks).isNotEmpty().anySatisfy(response ->
            assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello"));
    }

    @Test
    void propagatesChatEndpointErrors() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 401,
            "application/json", "{\"error\":{\"message\":\"Invalid key\"}}"));
        var model = provider.buildChatModel(config(baseUrl()), "test-key", "configured-model");
        StepVerifier.create(model.stream(new Prompt("Hi")))
            .expectError()
            .verify(Duration.ofSeconds(5));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private AiProvider config(String baseUrl) {
        var config = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("tokenhub-test");
        config.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("tokenhub");
        spec.setBaseUrl(baseUrl);
        config.setSpec(spec);
        return config;
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body)
        throws IOException {
        try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
