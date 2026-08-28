package run.halo.aifoundation.provider.gitee;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "gitee-moark",
    officialDocumentation = "https://ai.gitee.com/docs/products/apis/texts/text-generation/; "
        + "https://ai.gitee.com/docs/products/apis/embeddings/; "
        + "https://ai.gitee.com/v1/yaml",
    retrievedAt = "2026-08-27"
)
class GiteeProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GiteeProvider provider = new GiteeProvider();

    @Test
    void declaresProviderOwnedAdaptersAndKeepsChatAsDefault() {
        var aiProvider = provider("https://example.com/v1");

        assertThat(provider.getProviderType()).isEqualTo("gitee-moark");
        assertThat(provider.getDisplayName()).isEqualTo("Gitee 模力方舟");
        assertThat(provider.getSupportedAdapterTypes()).containsExactly(
            AdapterType.GITEE_CHAT, AdapterType.GITEE_RESPONSES,
            AdapterType.GITEE_MESSAGES, AdapterType.GITEE_EMBEDDING,
            AdapterType.RERANK, AdapterType.GITEE_IMAGE);
        assertThat(provider.maxEmbeddingsPerCall()).isEqualTo(1000);
        assertThat(provider.buildChatModel(aiProvider, "test-key", "Qwen3-32B"))
            .isInstanceOf(GiteeChatModel.class);
        assertThat(provider.buildChatModel(aiProvider, "test-key", new ProviderModelRef(
            "Qwen3-32B", ModelType.LANGUAGE, AdapterType.GITEE_RESPONSES)))
            .isInstanceOf(GiteeResponsesModel.class);
        var messages = (GiteeMessagesModel) provider.buildChatModel(aiProvider, "test-key",
            new ProviderModelRef("Qwen3-32B", ModelType.LANGUAGE,
                AdapterType.GITEE_MESSAGES));
        assertThat(messages).isInstanceOf(GiteeMessagesModel.class);
        assertThat(((AnthropicMessagesProfile) ReflectionTestUtils.getField(messages, "profile"))
            .providerType()).isEqualTo("gitee-moark");
        assertThat(((GiteeEmbeddingModel) provider.buildEmbeddingModel(aiProvider, "test-key",
            "Qwen3-Embedding-4B")).getOptions().customHeaders())
            .containsEntry(GiteeProvider.FAILOVER_HEADER, "false");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatConvertsJsonSchemaToGuidedJsonAndPreservesTools() {
        var format = ChatCompletionsOptions.ResponseFormat.builder()
            .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
            .name("answer")
            .jsonSchema("{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}}}")
            .strict(true)
            .build();
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .apiKey("test-key")
            .model("Qwen3-32B")
            .responseFormat(format)
            .extraBody(Map.of("guided_choice", List.of("yes", "no")))
            .build();
        var model = new GiteeChatModel(options, WebClient.builder());
        var prompt = new Prompt(List.of(new UserMessage("Answer")), options);

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);

        assertThat(body).doesNotContainKey("response_format")
            .containsEntry("guided_choice", List.of("yes", "no"));
        assertThat((Map<String, Object>) body.get("guided_json"))
            .containsEntry("type", "object");

        var callback = org.mockito.Mockito.mock(ToolCallback.class);
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
            .name("search").description("Search").inputSchema("{\"type\":\"object\"}").build());
        var withTools = options.mutate().toolCallbacks(List.of(callback)).build();
        var toolPrompt = new Prompt(List.of(new UserMessage("Answer")), withTools);
        var toolBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", toolPrompt, withTools, false);
        assertThat(toolBody).containsKeys("guided_json", "tools");
    }

    @Test
    void catalogUsesOperationsInsteadOfModelNameHeuristics() throws Exception {
        var authorization = new AtomicReference<String>();
        var query = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                query.set(exchange.getRequestURI().getQuery());
                var response = """
                    {"object":"list","data":[
                      {"id":"vision-model","operations":[
                        {"type":"text2text","path":"/chat/completions"},
                        {"type":"image2text","path":"/chat/completions"}]},
                      {"id":"vector-model","operations":[
                        {"type":"embeddings","path":"/embeddings"}]},
                      {"id":"rank-model","operations":[
                        {"type":"rerank","path":"/rerank"}]},
                      {"id":"paint-model","operations":[
                        {"type":"text2image","path":"/images/generations"}]},
                      {"id":"video-model","operations":[
                        {"type":"text2video","path":"/video/generations"}]}
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
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            StepVerifier.create(provider.discoverModels(provider(baseUrl), "test-key"))
                .assertNext(models -> {
                    assertThat(models).hasSize(4);
                    assertThat(models).extracting(model -> model.modelType()).containsExactly(
                        ModelType.LANGUAGE, ModelType.EMBEDDING, ModelType.RERANK,
                        ModelType.IMAGE_GENERATION);
                    assertThat(models.getFirst().adapterType()).isEqualTo(AdapterType.GITEE_CHAT);
                    assertThat(models.getFirst().features())
                        .containsExactlyInAnyOrder(ModelFeature.STREAMING, ModelFeature.VISION);
                    assertThat(models.get(1).adapterType())
                        .isEqualTo(AdapterType.GITEE_EMBEDDING);
                })
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(query.get()).isEqualTo("include_details=true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void multimodalEmbeddingUsesOfficialItemsAndRequestCanEnableFailover() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var failover = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            try (exchange) {
                failover.set(exchange.getRequestHeaders().getFirst(GiteeProvider.FAILOVER_HEADER));
                captured.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                var response = """
                    {"object":"list","model":"jina-embeddings-v4","data":[
                      {"index":0,"embedding":[0.1,0.2]},
                      {"index":1,"embedding":[0.3,0.4]}],
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
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            var model = new GiteeEmbeddingModel(new GiteeEmbeddingOptions(baseUrl, "test-key",
                "jina-embeddings-v4", 1024, null, false, false,
                Map.of("user", "halo-user"),
                Map.of(GiteeProvider.FAILOVER_HEADER, "false"), null), WebClient.builder());
            var response = model.call(new ProviderEmbeddingRequest(List.of(), List.of(
                EmbeddingContent.text("Halo CMS"),
                EmbeddingContent.image(DataContent.url("https://example.com/halo.png"))
            ), model.getOptions(), Map.of(GiteeProvider.FAILOVER_HEADER, "true")));

            assertThat(failover.get()).isEqualTo("true");
            assertThat(captured.get()).containsEntry("model", "jina-embeddings-v4")
                .containsEntry("dimensions", 1024)
                .containsEntry("user", "halo-user");
            assertThat((List<Map<String, Object>>) captured.get().get("input"))
                .containsExactly(Map.of("text", "Halo CMS"),
                    Map.of("image", "https://example.com/halo.png"));
            assertThat(response.getResults()).hasSize(2);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rerankAndImageExposeProviderNativeOptions() {
        var rerank = new GiteeRerankingClient("https://example.com/v1",
            "Qwen3-Reranker-4B", "test-key", WebClient.builder());
        var rerankRequest = RerankRequest.builder().query("Halo")
            .documents("first", "second").topN(1)
            .build();
        var rerankBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(rerank,
            "requestBody", rerankRequest, Map.of("return_documents", false));
        assertThat(rerankBody).containsEntry("model", "Qwen3-Reranker-4B")
            .containsEntry("documents", List.of("first", "second"))
            .containsEntry("top_n", 1)
            .containsEntry("return_documents", false);

        var multimodalRequest = RerankRequest.builder().query("Halo")
            .documents(List.of(
                RerankDocument.of("text document"),
                RerankDocument.builder()
                    .image(DataContent.data(new byte[] {1, 2, 3}, "image/png"))
                    .build()))
            .build();
        var multimodalEndpoint = (java.net.URI) ReflectionTestUtils.invokeMethod(rerank,
            "endpoint", multimodalRequest);
        var multimodalBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(rerank,
            "requestBody", multimodalRequest, Map.of());
        assertThat(multimodalEndpoint.getPath()).isEqualTo("/v1/rerank/multimodal");
        assertThat(multimodalBody.get("query")).isEqualTo(Map.of("text", "Halo"));
        assertThat((List<Map<String, Object>>) multimodalBody.get("documents"))
            .containsExactly(Map.of("text", "text document"),
                Map.of("image", "data:image/png;base64,AQID"));

        var image = new GiteeImageGenerationClient(new ImageGenerationClientOptions(
            "gitee-moark", "https://example.com/v1", "test-key", "Kolors",
            Map.of(GiteeProvider.FAILOVER_HEADER, "false")), WebClient.builder());
        var imageRequest = GenerateImageRequest.builder().prompt("A white cat")
            .images(List.of(DataContent.url("https://example.com/cat.png")))
            .size("1024x1024").n(2).responseFormat(ImageResponseFormat.BASE64)
            .build();
        var imageOptions = Map.<String, Object>of(
            "num_inference_steps", 25, "guidance_scale", 7.5);
        var imageBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(image,
            "requestBody", imageRequest, imageOptions);
        var result = (run.halo.aifoundation.image.GenerateImageResult)
            ReflectionTestUtils.invokeMethod(image, "imageResponse", """
                {"created":123,"data":[{"b64_json":"abc123",
                  "revised_prompt":"A refined white cat"}],
                 "usage":{"input_tokens":2,"output_tokens":3,"total_tokens":5}}
                """, imageRequest, imageOptions);

        assertThat(imageBody).containsEntry("image", "https://example.com/cat.png")
            .containsEntry("response_format", "b64_json")
            .containsEntry("num_inference_steps", 25)
            .containsEntry("guidance_scale", 7.5);
        assertThat(result.getImage().getBase64()).isEqualTo("abc123");
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(5);
        assertThat(result.getWarnings()).singleElement()
            .satisfies(warning -> assertThat(warning.getCode()).isEqualTo("prompt-revised"));
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("gitee-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("gitee-moark");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
