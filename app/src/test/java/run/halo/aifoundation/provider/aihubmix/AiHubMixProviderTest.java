package run.halo.aifoundation.provider.aihubmix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "aihubmix",
    officialDocumentation = "https://docs.aihubmix.com/en/api/responses/overview; "
        + "https://docs.aihubmix.com/en/api/Responses-API; "
        + "https://docs.aihubmix.com/cn/api-reference/anthropic-compatible/create-a-message; "
        + "https://docs.aihubmix.com/en/api/Models-API; "
        + "https://docs.aihubmix.com/en/api/App-code; "
        + "https://docs.aihubmix.com/en/api/Image-Gen; "
        + "https://docs.aihubmix.com/cn/api/Rerank; "
        + "https://docs.aihubmix.com/cn/api/Jina-AI",
    retrievedAt = "2026-08-27"
)
class AiHubMixProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AiHubMixProvider providerType = new AiHubMixProvider();

    @Test
    void declaresProviderOwnedAdaptersClientsAndResponsesDefault() {
        var provider = provider("https://example.com/v1");
        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(
            AdapterType.AIHUBMIX_RESPONSES, AdapterType.AIHUBMIX_CHAT,
            AdapterType.AIHUBMIX_MESSAGES, AdapterType.AIHUBMIX_EMBEDDING,
            AdapterType.RERANK, AdapterType.AIHUBMIX_IMAGE);
        assertThat(providerType.buildChatModel(provider, "key", "gpt-5"))
            .isInstanceOf(AiHubMixResponsesModel.class);
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "claude-sonnet", ModelType.LANGUAGE, AdapterType.AIHUBMIX_CHAT)))
            .isInstanceOf(AiHubMixChatModel.class);
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "claude-sonnet", ModelType.LANGUAGE, AdapterType.AIHUBMIX_MESSAGES)))
            .isInstanceOf(AiHubMixMessagesModel.class);
        assertThat(providerType.buildEmbeddingModel(provider, "key", "text-embedding-3-small"))
            .isInstanceOf(AiHubMixEmbeddingModel.class);
        assertThat(providerType.buildRerankingClient(provider, "key", "gte-rerank-v2"))
            .isInstanceOf(AiHubMixRerankingClient.class);
        assertThat(providerType.buildImageGenerationClient(provider, "key", "gpt-image-1.5"))
            .isInstanceOf(AiHubMixImageGenerationClient.class);
        assertThat(providerType.embeddingModelProviderOptions().providerOptionsNamespace())
            .isEqualTo("aihubmix");
        assertThat(providerType.languageModelProviderOptions().nativeStrictToolSchemas()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesCombinesDocumentedBuiltinToolsAndRejectsInvalidReasoning() {
        var generated = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder().prompt("Search")
                .providerOptions(Map.of("aihubmix", Map.of("builtinTools", List.of(
                    Map.of("type", "web_search_preview"),
                    Map.of("type", "code_interpreter", "container", Map.of("type", "auto")))))
                )
                .build());
        var options = generated.mutate().baseUrl("https://example.com/v1").model("gpt-5")
            .build();
        var body = responsesBody(options, new UserMessage("Search"));
        assertThat(body).doesNotContainKey("builtinTools");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .extracting(tool -> tool.get("type"))
            .containsExactly("web_search_preview", "code_interpreter");

        var invalidTool = options.mutate().extraBody(Map.of(
            "builtinTools", List.of(Map.of("type", "unofficial_search")))).build();
        assertThatThrownBy(() -> responsesBody(invalidTool, new UserMessage("Search")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("builtin tool type");

        var invalidEffort = options.mutate().model("claude-sonnet")
            .extraBody(Map.of("reasoning", Map.of("effort", "minimal"))).build();
        assertThat(responsesBody(invalidEffort, new UserMessage("Think")))
            .containsEntry("reasoning", Map.of("effort", "minimal"));

        assertThatThrownBy(() -> new AiHubMixChatProfile().customizeRequest(
            new java.util.LinkedHashMap<>(Map.of("builtinTools",
                List.of(Map.of("type", "web_search_preview")))),
            new Prompt(new UserMessage("Search")), options, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Responses adapter");
    }

    @Test
    void embeddingUsesAppCodeOptionsBase64AndUsage() throws Exception {
        var capture = new AtomicReference<Capture>();
        var server = server();
        server.createContext("/v1/embeddings", exchange -> {
            capture.set(capture(exchange));
            var encoded = Base64.getEncoder().encodeToString(ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN).putFloat(0.25f).putFloat(0.75f).array());
            respond(exchange, """
                {"id":"emb-1","model":"text-embedding-3-small",
                 "data":[{"index":0,"embedding":"%s"}],
                 "usage":{"prompt_tokens":3,"total_tokens":3}}
                """.formatted(encoded));
        });
        server.start();
        try {
            var model = providerType.buildEmbeddingModel(provider(baseUrl(server)), "sk-test",
                "text-embedding-3-small");
            var requestOptions = providerType.embeddingModelProviderOptions().buildOptions(
                EmbeddingRequest.builder().inputs(List.of("Halo")).dimensions(512)
                    .providerOptions(Map.of("aihubmix", Map.of("embedding_format", "base64")))
                    .build(), new java.util.ArrayList<>());
            EmbeddingResponse response = model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("Halo"), requestOptions));
            assertThat(response.getResult().getOutput()).containsExactly(0.25f, 0.75f);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(3);
            assertThat(capture.get().headers()).containsEntry("App-code", "NEUE3459")
                .containsEntry("Authorization", "Bearer sk-test");
            assertThat(capture.get().body()).containsEntry("dimensions", 512)
                .containsEntry("embedding_format", "base64");

            ((ProviderEmbeddingModel) model).call(new ProviderEmbeddingRequest(List.of(), List.of(
                EmbeddingContent.text("Halo"),
                EmbeddingContent.image(DataContent.data(new byte[] {1, 2, 3}, "image/png"))),
                requestOptions, Map.of()));
            assertThat(capture.get().body().get("input")).isEqualTo(List.of(
                Map.of("text", "Halo"),
                Map.of("image", Base64.getEncoder().encodeToString(
                    new byte[] {1, 2, 3}))));

            assertThat(model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("Halo"), new AiHubMixEmbeddingOptions(baseUrl(server), "key",
                    "future-embedding", 512, null, null, Map.of(), null))))
                .isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rerankUsesNativeTextContractAndAppCode() throws Exception {
        var capture = new AtomicReference<Capture>();
        var server = server();
        server.createContext("/v1/rerank", exchange -> {
            capture.set(capture(exchange));
            respond(exchange, """
                {"id":"rr-1","model":"gte-rerank-v2",
                 "results":[{"index":1,"relevance_score":0.98,"document":"Halo"}],
                 "usage":{"prompt_tokens":8,"total_tokens":8}}
                """);
        });
        server.start();
        try {
            var client = providerType.buildRerankingClient(provider(baseUrl(server)), "sk-test",
                "gte-rerank-v2");
            var response = client.rerank(RerankRequest.builder().query("CMS")
                .documents(List.of(RerankDocument.of("Other"), RerankDocument.of("Halo")))
                .topN(1).providerOptions(Map.of("aihubmix",
                    Map.of("return_documents", true))).build()).block();
            assertThat(response.getResults().getFirst().getScore()).isEqualTo(0.98);
            assertThat(response.getResults().getFirst().getDocument().getText()).isEqualTo("Halo");
            assertThat(capture.get().headers()).containsEntry("App-code", "NEUE3459");
            assertThat(capture.get().body()).containsEntry("model", "gte-rerank-v2")
                .containsEntry("return_documents", true);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageUsesProviderModelPredictionRouteAndFamilySpecificInput() throws Exception {
        var capture = new AtomicReference<Capture>();
        var server = server();
        server.createContext("/v1/models/google/imagen-4.0-generate-001/predictions", exchange -> {
            capture.set(capture(exchange));
            respond(exchange, """
                {"id":"pred-1","output":["https://example.com/image.png"]}
                """);
        });
        server.start();
        try {
            var client = providerType.buildImageGenerationClient(provider(baseUrl(server)),
                "sk-test", "imagen-4.0-generate-001");
            var result = client.generateImage(GenerateImageRequest.builder().prompt("Halo")
                .n(2).size("1024x1024")
                .providerOptions(Map.of("aihubmix", Map.of(
                    "model_path", "google/imagen-4.0-generate-001",
                    "output_format", "png",
                    "count_field", "numberOfImages")))
                .build()).block();
            assertThat(result.getImages()).singleElement().satisfies(image ->
                assertThat(image.getUrl()).isEqualTo("https://example.com/image.png"));
            assertThat(capture.get().path()).isEqualTo(
                "/v1/models/google/imagen-4.0-generate-001/predictions");
            assertThat(capture.get().headers()).containsEntry("App-code", "NEUE3459");
            var input = (Map<String, Object>) capture.get().body().get("input");
            assertThat(input).containsEntry("numberOfImages", 2)
                .doesNotContainKey("n");

            var unknown = providerType.buildImageGenerationClient(provider(baseUrl(server)),
                "key", "unknown-image");
            assertThatThrownBy(() -> unknown.generateImage(GenerateImageRequest.builder()
                .prompt("Halo").build()).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model_path");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void discoveryUsesNativeAdaptersCapabilitiesAndFiltersDeferredDomains() throws Exception {
        var capture = new AtomicReference<Capture>();
        var server = server();
        server.createContext("/api/v1/models", exchange -> {
            capture.set(capture(exchange));
            respond(exchange, """
                {"data":[
                  {"model_id":"gpt-5","types":"llm",
                   "features":"thinking,tools,structured_outputs",
                   "input_modalities":"text,image,pdf"},
                  {"model_id":"text-embedding-3-small","types":"embedding"},
                  {"model_id":"gte-rerank-v2","types":"rerank"},
                  {"model_id":"gpt-image-1.5","types":"image_generation"},
                  {"model_id":"video-model","types":"video"},
                  {"model_id":"voice-model","types":"tts"},
                  {"model_id":"speech-model","types":"stt"}]}
                """);
        });
        server.start();
        try {
            StepVerifier.create(providerType.discoverModels(provider(baseUrl(server)), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(4);
                    var language = models.getFirst();
                    assertThat(language.adapterType()).isEqualTo(AdapterType.AIHUBMIX_RESPONSES);
                    assertThat(language.features()).containsExactlyInAnyOrder(
                        ModelFeature.STREAMING, ModelFeature.VISION, ModelFeature.TOOL_CALL,
                        ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING);
                    assertThat(language.capabilities().getLanguage().getImageInput()).isTrue();
                    assertThat(language.capabilities().getLanguage().getFileInput()).isTrue();
                    assertThat(language.capabilities().getLanguage().getInputMediaTypes())
                        .containsExactly("image/*", "application/pdf");
                    assertThat(language.source()).isEqualTo(DiscoverySource.REMOTE);
                    assertThat(language.confidence()).isEqualTo(DiscoveryConfidence.HIGH);
                    assertThat(models).extracting(model -> model.adapterType())
                        .containsExactly(AdapterType.AIHUBMIX_RESPONSES,
                            AdapterType.AIHUBMIX_EMBEDDING, AdapterType.RERANK,
                            AdapterType.AIHUBMIX_IMAGE);
                    assertThat(models).extracting(model -> model.modelId())
                        .doesNotContain("video-model", "voice-model", "speech-model");
                }).verifyComplete();
            assertThat(capture.get().headers()).containsEntry("App-code", "NEUE3459")
                .containsEntry("Authorization", "Bearer sk-test");
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responsesBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new AiHubMixResponsesModel(options, WebClient.builder());
        var key = (String) ReflectionTestUtils.getField(model, "messageContextKey");
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model, "requestBody",
            options.mutate().toolContext(key, List.of(messages)).build(), false);
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        provider.setMetadata(new Metadata());
        provider.getMetadata().setName("aihubmix-test");
        provider.setSpec(new AiProvider.AiProviderSpec());
        provider.getSpec().setProviderType("aihubmix");
        provider.getSpec().setBaseUrl(baseUrl);
        return provider;
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private Capture capture(HttpExchange exchange) throws IOException {
        var headers = Map.of(
            "Authorization", value(exchange, "Authorization"),
            "App-code", value(exchange, "APP-Code"));
        var bytes = exchange.getRequestBody().readAllBytes();
        Map<String, Object> body = bytes.length == 0 ? Map.of()
            : OBJECT_MAPPER.readValue(bytes,
                new TypeReference<Map<String, Object>>() { });
        return new Capture(exchange.getRequestURI().getPath(), headers, body);
    }

    private String value(HttpExchange exchange, String name) {
        var value = exchange.getRequestHeaders().getFirst(name);
        return value != null ? value : "";
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record Capture(String path, Map<String, String> headers, Map<String, Object> body) {
    }
}
