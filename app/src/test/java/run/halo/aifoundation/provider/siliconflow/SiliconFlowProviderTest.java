package run.halo.aifoundation.provider.siliconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "siliconflow",
    officialDocumentation = "https://docs.siliconflow.com/en/api-reference/"
        + "chat-completions/chat-completions; https://docs.siliconflow.com/en/api-reference/"
        + "chat-completions/messages; https://docs.siliconflow.com/en/api-reference/"
        + "embeddings/create-embeddings; https://docs.siliconflow.com/en/api-reference/"
        + "rerank/create-rerank; https://docs.siliconflow.com/en/api-reference/"
        + "images/images-generations; https://docs.siliconflow.com/en/api-reference/"
        + "models/get-model-list",
    retrievedAt = "2026-08-27"
)
class SiliconFlowProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SiliconFlowProvider providerType = new SiliconFlowProvider();

    @Test
    void declaresDedicatedClientsAndUsesExplicitNativeReasoningAndFim() {
        var provider = provider("https://example.com/v1");
        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(
            AdapterType.SILICONFLOW_CHAT, AdapterType.SILICONFLOW_MESSAGES,
            AdapterType.SILICONFLOW_EMBEDDING,
            AdapterType.RERANK, AdapterType.SILICONFLOW_IMAGE);
        assertThat(providerType.getSupportedFeatures()).containsExactly(
            ModelFeature.STREAMING, ModelFeature.VISION, ModelFeature.AUDIO_INPUT,
            ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING);
        assertThat(providerType.buildChatModel(provider, "key", "Qwen/Qwen3-32B"))
            .isInstanceOf(SiliconFlowChatModel.class);
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "Qwen/Qwen3-32B", ModelType.LANGUAGE, AdapterType.SILICONFLOW_MESSAGES)))
            .isInstanceOf(SiliconFlowMessagesModel.class);
        assertThat(providerType.buildEmbeddingModel(provider, "key", "Qwen/Qwen3-Embedding-8B"))
            .isInstanceOf(SiliconFlowEmbeddingModel.class);
        assertThat(providerType.buildRerankingClient(provider, "key", "Qwen/Qwen3-Reranker-8B"))
            .isInstanceOf(SiliconFlowRerankingClient.class);
        assertThat(providerType.buildImageGenerationClient(provider, "key", "Qwen/Qwen-Image"))
            .isInstanceOf(SiliconFlowImageGenerationClient.class);

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("complete")
                .providerOptions(Map.of("siliconflow", Map.of(
                    "enable_thinking", true,
                    "thinking_budget", 4096,
                    "min_p", 0.05,
                    "prefix", "public int sum(",
                    "suffix", ") { return a + b; }")))
                .build());
        options = options.mutate().model("Qwen/Qwen3-32B").build();
        var body = requestBody(options, new UserMessage("complete"));
        assertThat(body).containsEntry("enable_thinking", true)
            .containsEntry("thinking_budget", 4096)
            .containsEntry("min_p", 0.05)
            .containsEntry("prefix", "public int sum(")
            .containsEntry("suffix", ") { return a + b; }");

        var invalidBody = new LinkedHashMap<String, Object>();
        invalidBody.put("model", "deepseek-ai/DeepSeek-V3.1");
        invalidBody.put("enable_thinking", true);
        invalidBody.put("tools", List.of(Map.of("type", "function")));
        new SiliconFlowChatProfile().customizeRequest(invalidBody,
            new Prompt(new UserMessage("Use a tool")),
            ChatCompletionsOptions.builder().build(), false);
        assertThat(invalidBody).containsEntry("enable_thinking", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatPreservesInterleavedReasoningAndStreamsToolInputs() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("deepseek-ai/DeepSeek-V3.2")
            .build();
        var assistant = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", "check the tool"))
            .toolCalls(List.of(new AssistantMessage.ToolCall(
                "call-1", "function", "lookup", "{\"q\":\"Halo\"}")))
            .build();
        var replay = requestBody(options, assistant);
        var messages = (List<Map<String, Object>>) replay.get("messages");
        assertThat(messages.getFirst()).containsEntry("reasoning_content", "check the tool");

        var model = new SiliconFlowChatModel(options, WebClient.builder());
        var chunks = Flux.just(
            """
                {"id":"chat-1","model":"deepseek-ai/DeepSeek-V3.2","choices":[{"index":0,
                 "delta":{"reasoning_content":"Need lookup"}}]}
                """,
            """
                {"id":"chat-1","model":"deepseek-ai/DeepSeek-V3.2","choices":[{"index":0,
                 "delta":{"tool_calls":[{"index":0,"id":"call-2","type":"function",
                   "function":{"name":"lookup","arguments":"{\\\"q\\\""}}]}}]}
                """,
            """
                {"id":"chat-1","model":"deepseek-ai/DeepSeek-V3.2","choices":[{"index":0,
                 "delta":{"tool_calls":[{"index":0,"function":{"arguments":":\\\"Halo\\\"}"}}]},
                 "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":3,"total_tokens":8}}
                """);
        var stream = (Flux<ProviderStreamPart>) ReflectionTestUtils.invokeMethod(model,
            "providerStreamParts", chunks, options);
        var parts = stream.collectList().block();
        assertThat(parts).contains(
            new ProviderStreamPart.ToolInputStartPart(0, "call-2", "lookup"),
            new ProviderStreamPart.ToolInputDeltaPart(0, "{\"q\""),
            new ProviderStreamPart.ToolInputDeltaPart(0, ":\"Halo\"}"),
            new ProviderStreamPart.ToolInputEndPart(0));
        assertThat(parts.stream()
            .filter(ProviderStreamPart.ChatResponsePart.class::isInstance)
            .map(ProviderStreamPart.ChatResponsePart.class::cast)
            .map(ProviderStreamPart.ChatResponsePart::response))
            .anySatisfy(response -> assertThat(response.getResult().getOutput().getMetadata())
                .containsEntry("reasoningContent", "Need lookup"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatEncodesCurrentImageVideoAndAudioContentParts() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("configured-multimodal-model")
            .build();
        var user = UserMessage.builder().text("Describe")
            .media(List.of(
                Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
                    .data(new byte[] {1}).build(),
                Media.builder().mimeType(MimeType.valueOf("video/mp4"))
                    .data(URI.create("https://example.com/video.mp4")).build(),
                Media.builder().mimeType(MimeType.valueOf("audio/wav"))
                    .data(URI.create("https://example.com/audio.wav")).build()))
            .build();

        var body = requestBody(options, user);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var parts = (List<Map<String, Object>>) messages.getFirst().get("content");
        assertThat(parts).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url", "audio_url");
        assertThat((Map<String, Object>) parts.get(2).get("video_url"))
            .containsEntry("url", "https://example.com/video.mp4");
        assertThat((Map<String, Object>) parts.get(3).get("audio_url"))
            .containsEntry("url", "https://example.com/audio.wav");
    }

    @Test
    void embeddingValidatesDimensionsAndPreservesBase64Usage() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/v1/embeddings", exchange -> {
            captured.set(readBody(exchange));
            var vector = ByteBuffer.allocate(2 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(0.25f).putFloat(0.75f).array();
            respond(exchange, """
                {"object":"list","model":"Qwen/Qwen3-Embedding-8B",
                 "data":[{"object":"embedding","index":0,"embedding":"%s"}],
                 "usage":{"prompt_tokens":3,"completion_tokens":0,"total_tokens":3}}
                """.formatted(Base64.getEncoder().encodeToString(vector)));
        });
        server.start();
        try {
            var model = providerType.buildEmbeddingModel(provider(baseUrl(server)), "sk-test",
                "Qwen/Qwen3-Embedding-8B");
            var options = providerType.embeddingModelProviderOptions().buildOptions(
                EmbeddingRequest.builder().inputs(List.of("Halo")).dimensions(1024)
                    .providerOptions(Map.of("siliconflow", Map.of(
                        "encoding_format", "base64")))
                    .build(), new java.util.ArrayList<>());
            var response = model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("Halo"), options));
            assertThat(response.getResult().getOutput()).containsExactly(0.25f, 0.75f);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(3);
            assertThat(captured.get()).containsEntry("dimensions", 1024)
                .containsEntry("encoding_format", "base64");

            var vlOptions = providerType.embeddingModelProviderOptions().buildOptions(
                EmbeddingRequest.builder().contents(List.of(
                        EmbeddingContent.text("Halo"),
                        EmbeddingContent.image(DataContent.data(
                            new byte[] {1, 2, 3}, "image/png"))))
                    .providerOptions(Map.of("siliconflow", Map.of(
                        "user", "halo-user", "truncate", "right")))
                    .build(), new java.util.ArrayList<>());
            ((ProviderEmbeddingModel) model).call(new ProviderEmbeddingRequest(List.of(), List.of(
                EmbeddingContent.text("Halo"),
                EmbeddingContent.image(DataContent.url("https://example.com/image.png"))),
                vlOptions, Map.of()));
            assertThat(captured.get()).containsEntry("user", "halo-user")
                .containsEntry("truncate", "right");
            assertThat(captured.get().get("input")).isEqualTo(List.of(
                Map.of("text", "Halo"),
                Map.of("image", "https://example.com/image.png")));

            var invalid = new SiliconFlowEmbeddingOptions(baseUrl(server), "key",
                "future-embedding", 1024, null, null, null, null, false, false,
                Map.of(), null);
            assertThat(model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                List.of("Halo"), invalid))).isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rerankMapsChunkOptionsAndTopLevelTokenUsage() throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/v1/rerank", exchange -> {
            captured.set(readBody(exchange));
            respond(exchange, """
                {"id":"rerank-1","results":[{"index":1,"relevance_score":0.95,
                  "document":{"text":"Halo"}}],
                 "tokens":{"input_tokens":8,"output_tokens":2}}
                """);
        });
        server.start();
        try {
            var client = providerType.buildRerankingClient(provider(baseUrl(server)), "sk-test",
                "BAAI/bge-reranker-v2-m3");
            var response = client.rerank(RerankRequest.builder().query("CMS")
                .documents(List.of(RerankDocument.of("Other"), RerankDocument.of("Halo")))
                .topN(1)
                .providerOptions(Map.of("siliconflow", Map.of(
                    "max_chunks_per_doc", 4, "overlap_tokens", 64,
                    "return_documents", false)))
                .build()).block();
            assertThat(response.getResults().getFirst().getIndex()).isEqualTo(1);
            assertThat(response.getUsage().getInputTokens()).isEqualTo(8);
            assertThat(response.getUsage().getTotalTokens()).isEqualTo(10);
            assertThat(response.getProviderMetadata()).containsKey("tokens");
            assertThat(captured.get()).containsEntry("max_chunks_per_doc", 4)
                .containsEntry("overlap_tokens", 64)
                .containsEntry("return_documents", false);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void imageUsesCurrentModelSpecificFieldsAndResponseFormat() {
        var qwen = new SiliconFlowImageGenerationClient(
            imageOptions("Qwen/Qwen-Image-Edit"), WebClient.builder());
        var qwenBody = qwen.requestBody(GenerateImageRequest.builder()
            .prompt("Edit")
            .images(List.of(DataContent.url("https://example.com/input.png", "image/png")))
            .n(4)
            .size("1328x1328")
            .negativePrompt("blur")
            .providerOptions(Map.of("siliconflow", Map.of(
                "cfg", 4.0, "num_inference_steps", 50)))
            .build());
        assertThat(qwenBody).containsEntry("image", "https://example.com/input.png")
            .containsEntry("batch_size", 4)
            .containsEntry("image_size", "1328x1328")
            .containsEntry("negative_prompt", "blur")
            .containsEntry("cfg", 4.0);
        var flux = new SiliconFlowImageGenerationClient(
            imageOptions("black-forest-labs/FLUX.2-pro"), WebClient.builder());
        var responseRequest = GenerateImageRequest.builder().prompt("Draw")
            .providerOptions(Map.of("siliconflow", Map.of("output_format", "jpeg")))
            .build();
        assertThat(flux.requestBody(responseRequest)).containsEntry("output_format", "jpeg");
        var response = flux.imageResponse("""
            {"images":[{"url":"https://example.com/result.jpg"}],
             "timings":{"inference":1.2},"seed":42}
            """, responseRequest);
        assertThat(response.getImages().getFirst().getMediaType()).isEqualTo("image/jpeg");
        assertThat(((Map<?, ?>) response.getUsage().getRaw()).get("seed")).isEqualTo(42L);

        var kontext = new SiliconFlowImageGenerationClient(
            imageOptions("black-forest-labs/FLUX.1-Kontext-pro"), WebClient.builder());
        assertThat(kontext.requestBody(GenerateImageRequest.builder().prompt("Restyle")
            .images(List.of(DataContent.data(new byte[] {1, 2, 3}, "image/png")))
            .aspectRatio("16:9")
            .providerOptions(Map.of("siliconflow", Map.of("image_field", "input_image")))
            .build())).containsKeys("input_image", "aspect_ratio").doesNotContainKey("image");
        assertThat(kontext.requestBody(GenerateImageRequest.builder()
            .prompt("Restyle").size("1024x576").build()))
            .containsEntry("image_size", "1024x576");
    }

    @Test
    void discoveryUsesOnlySupportedTypedDomainsAndMergesImageCapabilities() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = server();
        server.createContext("/v1/models", exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            requests.add(query);
            var body = switch (query) {
                case "sub_type=chat" -> "{\"data\":[{\"id\":\"Qwen/Qwen3-32B\"}]}";
                case "sub_type=embedding" ->
                    "{\"data\":[{\"id\":\"Qwen/Qwen3-Embedding-8B\"}]}";
                case "sub_type=reranker" ->
                    "{\"data\":[{\"id\":\"Qwen/Qwen3-Reranker-8B\"}]}";
                case "sub_type=text-to-image", "sub_type=image-to-image" ->
                    "{\"data\":[{\"id\":\"Qwen/Qwen-Image-Edit\"}]}";
                default -> "{\"data\":[]}";
            };
            respond(exchange, body);
        });
        server.start();
        try {
            StepVerifier.create(providerType.discoverModels(provider(baseUrl(server)), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(4);
                    assertThat(models).extracting(model -> model.adapterType())
                        .containsExactly(AdapterType.SILICONFLOW_CHAT,
                            AdapterType.SILICONFLOW_EMBEDDING, AdapterType.RERANK,
                            AdapterType.SILICONFLOW_IMAGE);
                    assertThat(models.getFirst().modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(models.getFirst().features()).containsExactly(ModelFeature.STREAMING);
                    var image = models.getLast().capabilities().getImageGeneration();
                    assertThat(image.getTextToImage()).isTrue();
                    assertThat(image.getImageToImage()).isTrue();
                    assertThat(image.getMaskInput()).isNull();
                    assertThat(image.getMaxImagesPerCall()).isNull();
                    assertThat(image.getSizes()).isNull();
                    assertThat(image.getAspectRatios()).isNull();
                    assertThat(image.getOutputMediaTypes()).isNull();
                })
                .verifyComplete();
            assertThat(requests).containsExactlyInAnyOrder(
                "sub_type=chat", "sub_type=embedding", "sub_type=reranker",
                "sub_type=text-to-image", "sub_type=image-to-image");
            assertThat(requests).noneMatch(query -> query.contains("audio")
                || query.contains("video"));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new SiliconFlowChatModel(options, WebClient.builder());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(List.of(messages), options), options, false);
    }

    private ImageGenerationClientOptions imageOptions(String model) {
        return new ImageGenerationClientOptions("siliconflow", "https://example.com/v1",
            "key", model, null);
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        return OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class);
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("siliconflow-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("siliconflow");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
