package run.halo.aifoundation.provider.ernie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "ernie",
    officialDocumentation = "https://cloud.baidu.com/doc/qianfan-api/s/vmhejnuy8; "
        + "https://cloud.baidu.com/doc/qianfan-docs/s/6mh3e6gjp",
    retrievedAt = "2026-08-27"
)
class ErnieProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ErnieProvider provider = new ErnieProvider();

    @Test
    void responsesIsRecommendedWhileChatRemainsExplicitlySelectable() {
        var aiProvider = provider("https://example.com/v2");

        assertThat(provider.buildChatModel(aiProvider, "test-key", "qwen3-14b"))
            .isInstanceOf(ErnieResponsesModel.class);
        assertThat(provider.buildChatModel(aiProvider, "test-key", new ProviderModelRef(
            "qwen3-14b", ModelType.LANGUAGE, AdapterType.ERNIE_CHAT)))
            .isInstanceOf(ErnieChatModel.class);
        assertThat(provider.buildChatModel(aiProvider, "test-key", new ProviderModelRef(
            "qwen3-14b", ModelType.LANGUAGE, AdapterType.ERNIE_MESSAGES)))
            .isInstanceOf(ErnieMessagesModel.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesDefaultsToStatelessStorageAndCombinesNativeTools() {
        var callback = org.mockito.Mockito.mock(ToolCallback.class);
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
            .name("local_search")
            .description("Search Halo")
            .inputSchema("{\"type\":\"object\",\"properties\":{}}")
            .build());
        var builtin = Map.<String, Object>of(
            "type", "knowledge_search", "knowledgebase_ids", "kb-1");
        var generated = languageOptions(GenerateTextRequest.builder()
            .prompt("Search product knowledge")
            .providerOptions(Map.of("ernie", Map.of(
                "builtinTools", List.of(builtin),
                "thinking", Map.of("type", "disabled"))))
            .build());
        var options = generated.mutate()
            .baseUrl("https://example.com/v2")
            .apiKey("test-key")
            .model("qwen3-14b")
            .toolCallbacks(List.of(callback))
            .toolContext("ernie-responses.messages", List.of(new UserMessage("Halo")))
            .build();
        var model = new ErnieResponsesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);

        assertThat(body).containsEntry("store", false)
            .containsEntry("thinking", Map.of("type", "disabled"))
            .doesNotContainKey("builtinTools");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .extracting(tool -> tool.get("type"))
            .containsExactly("function", "knowledge_search");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatConvertsThinkingAndKeepsSearchCacheAndBudgetOptions() {
        var generated = languageOptions(GenerateTextRequest.builder()
            .prompt("Search the web")
            .providerOptions(Map.of("ernie", Map.of(
                "enable_thinking", false,
                "web_search", Map.of("enable", true, "search_number", 6),
                "cache_id", "cache-1",
                "thinking_budget", 1024,
                "reasoning_effort", "max")))
            .build());
        var model = new ErnieChatModel(generated, WebClient.builder());
        var prompt = new org.springframework.ai.chat.prompt.Prompt(
            List.of(new UserMessage("latest Halo release")), generated);

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, generated, false);

        assertThat(body).containsEntry("enable_thinking", false)
            .containsEntry("cache_id", "cache-1")
            .containsEntry("thinking_budget", 1024)
            .containsEntry("reasoning_effort", "max")
            .containsEntry("web_search", Map.of("enable", true, "search_number", 6))
            .doesNotContainKeys("thinking", "store");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatPreservesSearchResultsAndDetailedCacheReasoningUsage() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v2")
            .apiKey("test-key")
            .model("deepseek-v3.2")
            .build();
        var model = new ErnieChatModel(options, WebClient.builder());

        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", """
                {"id":"chat-1","model":"deepseek-v3.2","choices":[{
                  "index":0,"finish_reason":"stop","message":{
                    "role":"assistant","content":"Halo 2.0",
                    "reasoning_content":"Check official sources."}}],
                 "search_results":[{"title":"Halo","url":"https://www.halo.run"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,
                   "prompt_tokens_details":{"cached_tokens":4,"search_tokens":2,
                     "plugin_tokens":1},
                   "completion_tokens_details":{"reasoning_tokens":3}}}
                """, options, "diagnostic-1");

        assertThat((List<Map<String, Object>>) response.getMetadata().get("search_results"))
            .singleElement().satisfies(source -> assertThat(source)
                .containsEntry("title", "Halo").containsEntry("url", "https://www.halo.run"));
        assertThat(response.getResult().getOutput().getMetadata())
            .containsEntry("reasoningContent", "Check official sources.");
        var rawUsage = (Map<String, Object>) response.getMetadata().getUsage().getNativeUsage();
        assertThat((Map<String, Object>) rawUsage.get("prompt_tokens_details"))
            .containsEntry("cached_tokens", 4)
            .containsEntry("search_tokens", 2)
            .containsEntry("plugin_tokens", 1);
        assertThat((Map<String, Object>) rawUsage.get("completion_tokens_details"))
            .containsEntry("reasoning_tokens", 3);
    }

    @Test
    void modelCatalogUsesOfficialTypesAndArchitectureModalities() throws Exception {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v2/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var response = """
                    {"object":"list","data":[
                      {"id":"vision-chat","type":"chat","architecture":{
                        "input_modalities":["text","image"],"output_modalities":["text"]}},
                      {"id":"qwen3-embedding-4b","type":"embeddings"},
                      {"id":"bce-reranker-base_v1","type":"rerank"},
                      {"id":"qwen-image","type":"text2image"},
                      {"id":"video-model","type":"text2video"}
                    ]}
                    """;
                var bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v2";
            StepVerifier.create(provider.discoverModels(provider(baseUrl), "test-key"))
                .assertNext(models -> {
                    assertThat(models).hasSize(4);
                    assertThat(models).extracting(model -> model.modelType())
                        .containsExactly(ModelType.LANGUAGE, ModelType.EMBEDDING,
                            ModelType.RERANK, ModelType.IMAGE_GENERATION);
                    assertThat(models.getFirst().adapterType())
                        .isEqualTo(AdapterType.ERNIE_RESPONSES);
                    assertThat(models.getFirst().features())
                        .containsExactlyInAnyOrder(ModelFeature.STREAMING, ModelFeature.VISION);
                    assertThat(models.get(1).adapterType())
                        .isEqualTo(AdapterType.ERNIE_EMBEDDING);
                    assertThat(models.get(2).adapterType()).isEqualTo(AdapterType.RERANK);
                    assertThat(models.get(3).adapterType()).isEqualTo(AdapterType.ERNIE_IMAGE);
                })
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void multimodalEmbeddingUsesJointTextImageObjectAndDataUrl() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v2/embeddings", exchange -> {
            try (exchange) {
                captured.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                var response = """
                    {"id":"emb-1","object":"list","created":123,"model":"gme-model",
                     "data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                     "usage":{"prompt_tokens":3,"total_tokens":3}}
                    """;
                var bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v2";
            var model = new ErnieEmbeddingModel(new ErnieEmbeddingOptions(baseUrl, "test-key",
                "gme-model", null, null, false, false, Map.of(), Map.of(), null),
                WebClient.builder());
            var requestOptions = new ErnieEmbeddingOptions(null, null, null, null, null,
                false, false, Map.of("user", "halo-user"), Map.of(), null);

            var response = model.call(new ProviderEmbeddingRequest(List.of(), List.of(
                EmbeddingContent.text("Halo CMS"),
                EmbeddingContent.image(DataContent.data(new byte[] {1, 2, 3}, "image/png"))
            ), requestOptions, Map.of()));

            assertThat(captured.get()).containsEntry("model", "gme-model")
                .containsEntry("encoding_format", "float")
                .containsEntry("user", "halo-user");
            assertThat((List<Map<String, Object>>) captured.get().get("input"))
                .singleElement().satisfies(input -> {
                    assertThat(input).containsEntry("text", "Halo CMS");
                    assertThat(input.get("image").toString())
                        .startsWith("data:image/png;base64,");
                });
            assertThat(response.getResult().getOutput()).containsExactly(0.1f, 0.2f);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rerankMapsOfficialBodyAndEnforcesDocumentLimits() {
        var client = new ErnieRerankingClient("https://example.com/v2", "bce-reranker-base_v1",
            "test-key", WebClient.builder());
        var request = RerankRequest.builder()
            .query("Halo")
            .documents("first", "second")
            .topN(1)
            .providerOptions(Map.of("ernie", Map.of("return_documents", true)))
            .build();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(client,
            "requestBody", request);
        assertThat(body).containsEntry("model", "bce-reranker-base_v1")
            .containsEntry("query", "Halo")
            .containsEntry("documents", List.of("first", "second"))
            .containsEntry("top_n", 1)
            .containsEntry("return_documents", true);

        var tooMany = RerankRequest.builder().query("Halo")
            .documents(java.util.stream.IntStream.range(0, 65)
                .mapToObj(index -> run.halo.aifoundation.rerank.RerankDocument.of(
                    "document-" + index)).toList())
            .build();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(client, "endpoint", tooMany))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most 64 documents");
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageAdapterUsesGenerationAndEditEndpointsWithNativeOptions() {
        var client = new ErnieImageGenerationClient(new ImageGenerationClientOptions(
            "ernie", "https://example.com/v2", "test-key", "qwen-image-edit", null),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Use image one's subject and image two's clothes")
            .images(List.of(
                DataContent.url("https://example.com/one.png", "image/png"),
                DataContent.url("https://example.com/two.png", "image/png")))
            .negativePrompt("blur")
            .seed(42)
            .providerOptions(Map.of("ernie", Map.of("prompt_extend", true, "watermark", false)))
            .build();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(client,
            "requestBody", request);
        var endpoint = (String) ReflectionTestUtils.invokeMethod(client,
            "endpointPath", request);
        var result = (run.halo.aifoundation.image.GenerateImageResult)
            ReflectionTestUtils.invokeMethod(client, "imageResponse", """
                {"created":123,"data":[{"url":"https://example.com/output.png",
                  "revised_prompt":"A refined prompt"}]}
                """, request);

        assertThat(endpoint).isEqualTo("/images/edits");
        assertThat(body).containsEntry("image", List.of("https://example.com/one.png",
                "https://example.com/two.png"))
            .containsEntry("negative_prompt", "blur")
            .containsEntry("seed", 42)
            .containsEntry("prompt_extend", true)
            .containsEntry("watermark", false);
        assertThat(result.getImages()).singleElement().satisfies(image -> {
            assertThat(image.getUrl()).isEqualTo("https://example.com/output.png");
            assertThat(image.getMetadata()).containsEntry("urlExpiresInSeconds", 86400);
        });
    }

    @Test
    void rejectsUnsupportedEmbeddingAndImageShapes() {
        var embedding = new ErnieEmbeddingModel(new ErnieEmbeddingOptions(
            "https://example.com/v2", "test-key", "gme-model", null, null, false, false,
            Map.of(), Map.of(), null), WebClient.builder());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(embedding,
            "multimodalRequestBody", List.of(EmbeddingContent.video(
                DataContent.url("https://example.com/video.mp4"))), embedding.getOptions()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("do not support video");

        var image = new ErnieImageGenerationClient(new ImageGenerationClientOptions(
            "ernie", "https://example.com/v2", "test-key", "qwen-image-edit", null),
            WebClient.builder());
        var dataEdit = GenerateImageRequest.builder().prompt("edit")
            .images(List.of(DataContent.data(new byte[] {1}, "image/png")))
            .build();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(image,
            "requestBody", dataEdit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider-accessible image URLs");
    }

    private ChatCompletionsOptions languageOptions(GenerateTextRequest request) {
        return (ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(request);
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("ernie-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("ernie");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
