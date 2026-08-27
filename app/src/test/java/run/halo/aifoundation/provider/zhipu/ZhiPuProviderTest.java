package run.halo.aifoundation.provider.zhipu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "zhipuai",
    officialDocumentation = "https://docs.bigmodel.cn/api-reference/模型-api/对话补全; "
        + "https://docs.bigmodel.cn/cn/guide/capabilities/thinking-mode; "
        + "https://docs.bigmodel.cn/cn/guide/develop/claude/introduction; "
        + "https://docs.bigmodel.cn/cn/guide/capabilities/stream-tool; "
        + "https://docs.bigmodel.cn/cn/guide/tools/web-search; "
        + "https://docs.bigmodel.cn/api-reference/模型-api/文本嵌入; "
        + "https://docs.bigmodel.cn/api-reference/模型-api/文本重排序; "
        + "https://docs.bigmodel.cn/api-reference/模型-api/图像生成; "
        + "https://docs.bigmodel.cn/cn/guide/start/model-overview",
    retrievedAt = "2026-08-27"
)
class ZhiPuProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ZhiPuProvider providerType = new ZhiPuProvider();

    @Test
    void declaresFourDedicatedClientsAndProviderOwnedOptions() {
        var provider = provider("https://example.com/api/paas/v4");
        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(
            AdapterType.ZHIPU_CHAT, AdapterType.ZHIPU_MESSAGES, AdapterType.ZHIPU_EMBEDDING,
            AdapterType.RERANK, AdapterType.ZHIPU_IMAGE);
        assertThat(providerType.buildChatModel(provider, "key", "glm-5.3"))
            .isInstanceOf(ZhiPuChatModel.class);
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "glm-5.3", ModelType.LANGUAGE, AdapterType.ZHIPU_MESSAGES)))
            .isInstanceOf(ZhiPuMessagesModel.class);
        assertThat(providerType.buildEmbeddingModel(provider, "key", "embedding-3"))
            .isInstanceOf(ZhiPuEmbeddingModel.class);
        assertThat(providerType.buildRerankingClient(provider, "key", "rerank"))
            .isInstanceOf(ZhiPuRerankingClient.class);
        assertThat(providerType.buildImageGenerationClient(provider, "key", "glm-image"))
            .isInstanceOf(ZhiPuImageGenerationClient.class);
        assertThat(providerType.embeddingModelProviderOptions().providerOptionsNamespace())
            .isEqualTo("zhipuai");
        assertThat(providerType.languageModelProviderOptions().nativeStrictToolSchemas()).isFalse();
        assertThat(providerType.languageModelProviderOptions().structuredOutputSupport())
            .isEqualTo(run.halo.aifoundation.provider.support.StructuredOutputSupport.JSON_OBJECT);
        assertThat(providerType.languageModelProviderOptions(AdapterType.ZHIPU_MESSAGES)
            .structuredOutputSupport())
            .isEqualTo(run.halo.aifoundation.provider.support.StructuredOutputSupport.PROMPT_ONLY);
        assertThat(providerType.getSupportedFeatures(AdapterType.ZHIPU_MESSAGES))
            .contains(ModelFeature.VISION)
            .doesNotContain(ModelFeature.AUDIO_INPUT);

        var options = providerType.languageModelProviderOptions(AdapterType.ZHIPU_CHAT);
        assertThat(options.reasoningControlOptions().enabledSupported()).isFalse();
        assertThat(options.reasoningControlOptions().supportedEfforts()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatUsesExplicitNativeOptionsWithoutInspectingModelId() {
        var builtin = Map.<String, Object>of(
            "type", "web_search",
            "web_search", Map.of(
                "search_engine", "search_pro",
                "count", 3,
                "search_recency_filter", "oneWeek",
                "search_result", true));
        var request = GenerateTextRequest.builder()
            .prompt("Research Halo")
            .providerOptions(Map.of("zhipuai", Map.of(
                "thinking", Map.of("clear_thinking", false, "type", "enabled"))))
            .build();
        assertThat((ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(request)).extracting(ChatCompletionsOptions::getExtraBody)
            .satisfies(value -> assertThat((Map<String, Object>) value)
                .containsKey("thinking"));

        var portable = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("Research Halo")
                .providerOptions(Map.of("zhipuai", Map.of("builtinTools", List.of(builtin))))
                .build());
        portable = portable.mutate().baseUrl("https://example.com/api/paas/v4")
            .model("glm-5.3").build();
        var body = chatBody(portable, true, new UserMessage("Research Halo"));
        assertThat(body).doesNotContainKeys("reasoning_effort", "thinking", "tool_stream",
            "stream_options", "parallel_tool_calls");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .singleElement().satisfies(tool -> {
                assertThat(tool).containsEntry("type", "web_search");
                assertThat((Map<String, Object>) tool.get("web_search"))
                    .containsEntry("search_engine", "search_pro")
                    .containsEntry("count", 3);
            });

        var medium = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder().prompt("Think").build());
        medium = medium.mutate().model("glm-5.2").build();
        assertThat(chatBody(medium, false, new UserMessage("Think")))
            .doesNotContainKeys("reasoning_effort", "thinking");

        var invalid = portable.mutate().model("future-model")
            .extraBody(Map.of("reasoning_effort", "extreme")).build();
        assertThatThrownBy(() -> chatBody(invalid, false, new UserMessage("Think")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported Zhipu reasoning_effort");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatConvertsVisionFileAudioAndPreservesReasoningAndSources() {
        var visionOptions = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/api/paas/v4").model("glm-5v-turbo").build();
        var vision = UserMessage.builder().text("Inspect")
            .media(List.of(
                new Media(MimeType.valueOf("image/png"),
                    URI.create("https://example.com/image.png")),
                new Media(MimeType.valueOf("video/mp4"),
                    URI.create("https://example.com/video.mp4"))))
            .build();
        var body = chatBody(visionOptions, false, vision);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");
        assertThat(content).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url");

        var file = UserMessage.builder().text("Read")
            .media(List.of(new Media(MimeType.valueOf("application/pdf"),
                URI.create("https://example.com/file.pdf"))))
            .build();
        assertThat((List<Map<String, Object>>) ((List<Map<String, Object>>)
            chatBody(visionOptions, false, file).get("messages")).getFirst().get("content"))
            .anySatisfy(part -> assertThat(part).containsEntry("type", "file_url"));

        var audioOptions = visionOptions.mutate().model("glm-4-voice").build();
        var audio = UserMessage.builder().text("Repeat")
            .media(List.of(new Media(MimeType.valueOf("audio/wav"),
                new ByteArrayResource(new byte[] {1, 2, 3}))))
            .build();
        var audioBody = chatBody(audioOptions, false, audio);
        var audioParts = (List<Map<String, Object>>) ((List<Map<String, Object>>)
            audioBody.get("messages")).getFirst().get("content");
        assertThat((Map<String, Object>) audioParts.get(1).get("input_audio"))
            .containsEntry("data", "AQID").containsEntry("format", "wav");
        assertThat(chatBody(visionOptions, false, audio)).containsKey("messages");

        var assistant = AssistantMessage.builder().content("Answer")
            .properties(Map.of("reasoningContent", "original chain")).build();
        var replay = chatBody(visionOptions.mutate().model("glm-5.2").build(), false, assistant);
        assertThat(((List<Map<String, Object>>) replay.get("messages")).getFirst())
            .containsEntry("reasoning_content", "original chain");

        var model = new ZhiPuChatModel(visionOptions, org.springframework.web.reactive.function.client.WebClient.builder());
        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", """
                {"id":"chat-1","request_id":"req-123456","model":"glm-5.2",
                 "choices":[{"index":0,"message":{"role":"assistant",
                   "reasoning_content":"verify","content":"grounded"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14,
                   "prompt_tokens_details":{"cached_tokens":3}},
                 "web_search":[{"title":"Halo","link":"https://halo.run"}],
                 "content_filter":[{"role":"assistant","level":3}]}
                """, visionOptions);
        assertThat(response.getResult().getOutput().getMetadata())
            .containsEntry("reasoningContent", "verify");
        assertThat((Object) response.getMetadata().get("web_search")).isInstanceOf(List.class);
        assertThat((Object) response.getMetadata().get("sources")).isInstanceOf(List.class);
        assertThat((Object) response.getMetadata().get("content_filter")).isInstanceOf(List.class);
        assertThat((Map<String, Object>) response.getMetadata().getUsage().getNativeUsage())
            .containsKey("prompt_tokens_details");
    }

    @Test
    void embeddingUsesNativeDimensionsAndPreservesUsage() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/api/paas/v4/embeddings", exchange -> {
            captured.set(readBody(exchange));
            respond(exchange, """
                {"object":"list","model":"embedding-3",
                 "data":[{"object":"embedding","index":0,"embedding":[0.25,0.75]}],
                 "usage":{"prompt_tokens":3,"completion_tokens":0,"total_tokens":3}}
                """);
        });
        server.start();
        try {
            var model = providerType.buildEmbeddingModel(provider(baseUrl(server)), "sk-test",
                "embedding-3");
            var options = providerType.embeddingModelProviderOptions().buildOptions(
                EmbeddingRequest.builder().inputs(List.of("Halo")).dimensions(512).build(),
                new java.util.ArrayList<>());
            var response = model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("Halo"), options));
            assertThat(response.getResult().getOutput()).containsExactly(0.25f, 0.75f);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(3);
            assertThat(captured.get()).containsEntry("model", "embedding-3")
                .containsEntry("dimensions", 512);

            var invalid = new ZhiPuEmbeddingOptions(baseUrl(server), "key",
                "embedding-2", 512, Map.of(), null);
            assertThat(model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("Halo"), invalid))).isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rerankValidatesNativeContractAndPreservesRawScores() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/api/paas/v4/rerank", exchange -> {
            captured.set(readBody(exchange));
            respond(exchange, """
                {"id":"rr-1","request_id":"request-123456","created":123,
                 "results":[{"index":1,"relevance_score":0.97,"document":"Halo"}],
                 "usage":{"prompt_tokens":8,"total_tokens":8}}
                """);
        });
        server.start();
        try {
            var client = providerType.buildRerankingClient(provider(baseUrl(server)), "sk-test",
                "rerank");
            var response = client.rerank(RerankRequest.builder().query("CMS")
                .documents(List.of(RerankDocument.of("Other"), RerankDocument.of("Halo")))
                .topN(1)
                .providerOptions(Map.of("zhipuai", Map.of(
                    "return_documents", true, "return_raw_scores", true,
                    "request_id", "request-123456")))
                .build()).block();
            assertThat(response.getResults().getFirst().getDocument().getText()).isEqualTo("Halo");
            assertThat(response.getResults().getFirst().getScore()).isEqualTo(0.97);
            assertThat(response.getUsage().getInputTokens()).isEqualTo(8);
            assertThat(captured.get()).containsEntry("model", "rerank")
                .containsEntry("return_raw_scores", true)
                .containsEntry("request_id", "request-123456");

            assertThatThrownBy(() -> client.rerank(RerankRequest.builder().query("CMS")
                .documents(java.util.Collections.nCopies(129, RerankDocument.of("doc")))
                .build()).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 128");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageUsesNativeQualityWatermarkAndContentFilterMetadata() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/api/paas/v4/images/generations", exchange -> {
            captured.set(readBody(exchange));
            respond(exchange, """
                {"created":123,"data":[{"url":"https://example.com/image.png"}],
                 "content_filter":[{"role":"assistant","level":3}]}
                """);
        });
        server.start();
        try {
            var client = providerType.buildImageGenerationClient(provider(baseUrl(server)),
                "sk-test", "glm-image");
            var result = client.generateImage(GenerateImageRequest.builder()
                .prompt("Halo logo").size(1280)
                .providerOptions(Map.of("zhipuai", Map.of(
                    "quality", "hd", "watermark_enabled", false,
                    "user_id", "user-123456")))
                .build()).block();
            assertThat(result.getImages()).singleElement().satisfies(image ->
                assertThat(image.getUrl()).isEqualTo("https://example.com/image.png"));
            assertThat((Map<String, Object>) result.getUsage().getRaw())
                .containsKeys("created", "content_filter");
            assertThat(captured.get()).containsEntry("model", "glm-image")
                .containsEntry("size", "1280x1280")
                .containsEntry("quality", "hd")
                .containsEntry("watermark_enabled", false);

            assertThat(client.generateImage(GenerateImageRequest.builder()
                .prompt("Halo").size(1025, 1024).build()).block()).isNotNull();
            assertThat(client.generateImage(GenerateImageRequest.builder()
                .prompt("Halo").providerOptions(Map.of("zhipuai",
                    Map.of("quality", "standard"))).build()).block()).isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatBody(ChatCompletionsOptions options, boolean stream,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new ZhiPuChatModel(options,
            org.springframework.web.reactive.function.client.WebClient.builder());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model, "requestBody",
            new Prompt(List.of(messages), options), options, stream);
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        provider.setMetadata(new Metadata());
        provider.getMetadata().setName("zhipu-test");
        provider.setSpec(new AiProvider.AiProviderSpec());
        provider.getSpec().setProviderType("zhipuai");
        provider.getSpec().setBaseUrl(baseUrl);
        return provider;
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    private String baseUrl(HttpServer server) {
        return "http://" + server.getAddress().getHostString() + ":"
            + server.getAddress().getPort() + "/api/paas/v4";
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        return OBJECT_MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() { });
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
