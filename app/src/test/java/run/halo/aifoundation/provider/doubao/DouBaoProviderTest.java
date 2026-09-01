package run.halo.aifoundation.provider.doubao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesWireCodec;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "doubao",
    officialDocumentation = "https://www.volcengine.com/docs/82379/1795150; "
        + "https://api.volcengine.com/api-docs/view?action=Embeddings&serviceCode=ark"
        + "&version=2024-01-01; "
        + "https://api.volcengine.com/api-docs/view?action=EmbeddingsMultimodal"
        + "&serviceCode=ark&version=2024-01-01",
    retrievedAt = "2026-09-01"
)
class DouBaoProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DouBaoProvider provider = new DouBaoProvider();

    @Test
    void responsesIsRecommendedWhileChatRemainsExplicitlySelectable() {
        var aiProvider = provider("https://example.com/api/v3");

        assertThat(provider.buildChatModel(aiProvider, "test-key", "doubao-seed"))
            .isInstanceOf(DouBaoResponsesModel.class);
        assertThat(provider.buildChatModel(aiProvider, "test-key", new ProviderModelRef(
            "doubao-seed", ModelType.LANGUAGE, AdapterType.DOUBAO_CHAT)))
            .isInstanceOf(DouBaoChatModel.class);
        assertThat(provider.getSupportedFeatures()).containsExactly(
            ModelFeature.STREAMING, ModelFeature.VISION, ModelFeature.AUDIO_INPUT,
            ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatAndResponsesUseCurrentArkMultimodalContentShapes() {
        var media = List.of(
            Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(new byte[] {1}).build(),
            Media.builder().mimeType(MimeType.valueOf("video/mp4"))
                .data(URI.create("https://example.com/video.mp4")).build(),
            Media.builder().mimeType(MimeType.valueOf("audio/mpeg"))
                .data(URI.create("https://example.com/audio.mp3")).build());
        var user = UserMessage.builder().text("Describe").media(media).build();
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/api/v3")
            .model("configured-multimodal-model")
            .build();

        var chat = new DouBaoChatModel(options, WebClient.builder());
        var chatBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(chat,
            "requestBody", new Prompt(List.of(user), options), options, false);
        var chatMessages = (List<Map<String, Object>>) chatBody.get("messages");
        var chatParts = (List<Map<String, Object>>) chatMessages.getFirst().get("content");
        assertThat(chatParts).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url", "input_audio");
        assertThat((Map<String, Object>) chatParts.get(3).get("input_audio"))
            .containsEntry("url", "https://example.com/audio.mp3")
            .doesNotContainKeys("data", "format");

        var responsesOptions = options.mutate()
            .toolContext("doubao-responses.messages", List.of(user))
            .build();
        var responses = new DouBaoResponsesModel(responsesOptions, WebClient.builder());
        var responsesBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(responses,
            "requestBody", responsesOptions, false);
        var input = (List<Map<String, Object>>) responsesBody.get("input");
        var responseParts = (List<Map<String, Object>>) input.getFirst().get("content");
        assertThat(responseParts).extracting(part -> part.get("type"))
            .containsExactly("input_text", "input_image", "input_video", "input_audio");
        assertThat(responseParts.get(2))
            .containsEntry("video_url", "https://example.com/video.mp4");
        assertThat(responseParts.get(3))
            .containsEntry("audio_url", "https://example.com/audio.mp3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesCombinesFunctionToolsWithArkBuiltinToolsAndNativeThinking() {
        var builtin = Map.<String, Object>of("type", "web_search");
        var callback = org.mockito.Mockito.mock(ToolCallback.class);
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
            .name("local_search")
            .description("Search local Halo content")
            .inputSchema("{\"type\":\"object\",\"properties\":{}}")
            .build());
        var generated = (ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("Find current Halo news")
                .build());
        var options = generated.mutate()
            .baseUrl("https://example.com/api/v3")
            .apiKey("test-key")
            .model("doubao-seed")
            .extraBody(Map.of(
                "builtinTools", List.of(builtin),
                "thinking", Map.of("type", "disabled")))
            .toolCallbacks(List.of(callback))
            .toolContext("doubao-responses.messages", List.of(new UserMessage("news")))
            .build();
        var model = new DouBaoResponsesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);

        assertThat(body).containsEntry("thinking", Map.of("type", "disabled"))
            .doesNotContainKey("builtinTools");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .extracting(tool -> tool.get("type"))
            .containsExactly("function", "web_search");
        assertThatThrownBy(() -> provider.languageModelProviderOptions()
            .reasoningControlOptions().validate("doubao", GenerateTextRequest.builder()
                .prompt("Think harder")
                .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
                .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires an explicit model parameter mapping");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesNormalizesDoubaoAppBlocksAndPreservesNativeToolItems() {
        var result = new ResponsesWireCodec(new DouBaoResponsesProfile()).decodeResponse("""
            {
              "id":"resp-1","model":"doubao-seed","status":"completed",
              "output":[
                {"type":"doubao_app_call","id":"app-1","feature":"deep_search",
                 "status":"completed","blocks":[
                   {"type":"reasoning_text","reasoning_text":"Search official sources."},
                   {"type":"search","results":[
                     {"title":"Halo","url":"https://www.halo.run"}]},
                   {"type":"output_text","text":"Halo is open source."}
                 ]},
                {"type":"knowledge_search_call","id":"ks-1","status":"completed",
                 "queries":["Halo CMS"],"knowledge_resource_id":"kb-1"},
                {"type":"mcp_call","id":"mcp-1","server_label":"docs",
                 "name":"search","arguments":"{}","output":"found"},
                {"type":"image_process","id":"img-1","status":"completed",
                 "action":{"type":"image_to_image"}}
              ],
              "usage":{"input_tokens":5,"output_tokens":7,"total_tokens":12,
                "tool_usage":{"knowledge_search":1}}
            }
            """);

        assertThat(result.text()).isEqualTo("Halo is open source.");
        assertThat(result.reasoning()).isEqualTo("Search official sources.");
        assertThat(result.sources()).singleElement().satisfies(source -> assertThat(source)
            .containsEntry("title", "Halo").containsEntry("url", "https://www.halo.run"));
        assertThat((List<Map<String, Object>>) result.providerMetadata()
            .get("providerOutputItems"))
            .extracting(item -> item.get("type"))
            .containsExactly("doubao_app_call", "knowledge_search_call", "mcp_call",
                "image_process");
        assertThat(result.usage().details()).containsEntry("tool_usage",
            Map.of("knowledge_search", 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multimodalEmbeddingUsesNativeEndpointAndPreservesUniqueOutputs() throws Exception {
        var capturedPath = new AtomicReference<String>();
        var capturedBody = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v3/embeddings/multimodal", exchange -> {
            try (exchange) {
                capturedPath.set(exchange.getRequestURI().getPath());
                capturedBody.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                var response = """
                    {
                      "id":"emb-mm-1","model":"doubao-embedding-vision",
                      "data":{
                        "embedding":[0.1,0.2],
                        "sparse_embedding":[{"index":7,"value":0.8}],
                        "multi_embedding":[[0.3,0.4],[0.5,0.6]]
                      },
                      "usage":{"prompt_tokens":9,"total_tokens":9,
                        "prompt_tokens_details":{"image_tokens":4}}
                    }
                    """;
                var bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3";
            var model = new DouBaoEmbeddingModel(DouBaoEmbeddingOptions.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .model("doubao-embedding-vision")
                .build(), WebClient.builder());
            var requestOptions = DouBaoEmbeddingOptions.builder()
                .dimensions(512)
                .instructions("Represent the product and scene together")
                .includeSparseEmbedding(true)
                .includeModalityEmbeddings(true)
                .build();

            var response = model.call(new ProviderEmbeddingRequest(List.of(), List.of(
                EmbeddingContent.text("Halo CMS"),
                EmbeddingContent.image(DataContent.url("https://example.com/halo.png")),
                EmbeddingContent.video(DataContent.url("https://example.com/demo.mp4"))
            ), requestOptions, Map.of("X-Trace", "contract")));

            assertThat(capturedPath.get()).isEqualTo("/api/v3/embeddings/multimodal");
            assertThat(capturedBody.get()).containsEntry("dimensions", 512)
                .containsEntry("instructions", "Represent the product and scene together")
                .containsEntry("sparse_embedding", Map.of("type", "enabled"))
                .containsEntry("multi_embedding", Map.of("type", "enabled"));
            assertThat((List<Map<String, Object>>) capturedBody.get().get("input"))
                .extracting(value -> value.get("type"))
                .containsExactly("text", "image_url", "video_url");
            assertThat(response.getResult().getOutput()).containsExactly(0.1f, 0.2f);
            assertThat((List<Map<String, Object>>) response.getMetadata().get("sparseEmbedding"))
                .singleElement().satisfies(value -> assertThat(value)
                    .containsEntry("index", 7).containsEntry("value", 0.8));
            assertThat((List<List<Double>>) response.getMetadata().get("modalityEmbeddings"))
                .containsExactly(List.of(0.3, 0.4), List.of(0.5, 0.6));
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(9);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void textEmbeddingUsesStandardArkEndpointAndKeepsInputOrder() {
        var model = new DouBaoEmbeddingModel(DouBaoEmbeddingOptions.builder()
            .baseUrl("https://example.com/api/v3")
            .apiKey("test-key")
            .model("doubao-embedding")
            .dimensions(256)
            .build(), WebClient.builder());
        var options = model.getOptions();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "textRequestBody", List.of("first", "second"), options);
        var response = (org.springframework.ai.embedding.EmbeddingResponse)
            ReflectionTestUtils.invokeMethod(model, "embeddingResponse", """
                {
                  "id":"emb-1","model":"doubao-embedding",
                  "data":[
                    {"index":1,"embedding":[0.3,0.4]},
                    {"index":0,"embedding":[0.1,0.2]}
                  ],
                  "usage":{"prompt_tokens":4,"total_tokens":4}
                }
                """, false, options.model());

        assertThat(body).containsEntry("input", List.of("first", "second"))
            .containsEntry("encoding_format", "float")
            .containsEntry("dimensions", 256);
        assertThat(response.getResults()).extracting(result -> result.getIndex())
            .containsExactly(0, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void embeddingAppliesDocumentedModelNativeEncodingFormatAndRejectsUnknownOptions() {
        var request = run.halo.aifoundation.embedding.EmbeddingRequest.builder()
            .inputs(List.of("Halo"))
            .build();
        var options = DouBaoEmbeddingOptionsFactory.build(request,
            new EmbeddingModelProviderOptions(null, Map.of("encoding_format", "base64")),
            new java.util.ArrayList<>());
        var model = new DouBaoEmbeddingModel(options.toBuilder()
            .baseUrl("https://example.com/api/v3")
            .model("configured-model")
            .build(), WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "textRequestBody", request.getInputs(), model.getOptions());

        assertThat(body).containsEntry("encoding_format", "base64");
        assertThatThrownBy(() -> DouBaoEmbeddingOptionsFactory.build(request,
            new EmbeddingModelProviderOptions(null, Map.of("future_option", true)),
            new java.util.ArrayList<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("future_option");
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageAdapterMapsSequentialGenerationAndOfficialUsage() {
        var client = new DouBaoImageGenerationClient(new ImageGenerationClientOptions(
            "doubao", "https://example.com/api/v3", "test-key", "doubao-seedream", null),
            WebClient.builder());
        var request = GenerateImageRequest.builder().prompt("A Halo mascot story").n(4).build();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(client,
            "requestBody", request);
        var result = (run.halo.aifoundation.image.GenerateImageResult)
            ReflectionTestUtils.invokeMethod(client, "imageResponse", """
                {
                  "id":"image-1","model":"doubao-seedream",
                  "data":[{"url":"https://example.com/1.png","output_format":"png"}],
                  "usage":{"input_images":0,"generated_images":1,"output_tokens":3,
                    "total_tokens":3,"tool_usage":{"web_search":1}}
                }
                """, request);

        assertThat(body).containsEntry("sequential_image_generation", "auto")
            .containsEntry("sequential_image_generation_options", Map.of("max_images", 4))
            .containsEntry("output_format", "png");
        assertThat(result.getUsage().getImageCount()).isEqualTo(1);
        assertThat((Map<String, Object>) result.getUsage().getRaw())
            .containsEntry("tool_usage", Map.of("web_search", 1));
    }

    @Test
    void embeddingContentRejectsInvalidShapesAndArkRequiresMediaUrls() {
        assertThatThrownBy(() -> EmbeddingContent.builder()
            .type(EmbeddingContent.Type.TEXT).media(DataContent.url("https://example.com/x"))
            .build()).isInstanceOf(IllegalArgumentException.class);

        var model = new DouBaoEmbeddingModel(DouBaoEmbeddingOptions.builder()
            .baseUrl("https://example.com/api/v3")
            .apiKey("test-key")
            .model("doubao-embedding-vision")
            .build(), WebClient.builder());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "multimodalRequestBody", List.of(EmbeddingContent.image(
                DataContent.data(new byte[] {1, 2}, "image/png"))), model.getOptions()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires a URL");
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("doubao-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("doubao");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
