package run.halo.aifoundation.provider.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "minimax",
    officialDocumentation = "https://platform.minimax.io/docs/api-reference/text-anthropic-api; "
        + "https://platform.minimax.io/docs/api-reference/text-openai-api; "
        + "https://platform.minimax.io/docs/api-reference/responses-create; "
        + "https://platform.minimax.io/docs/api-reference/models/openai/list-models; "
        + "https://platform.minimax.io/docs/api-reference/image-generation-t2i",
    retrievedAt = "2026-08-27"
)
class MiniMaxProviderTest {

    private final MiniMaxProvider provider = new MiniMaxProvider();

    @Test
    void defaultsToMessagesAndKeepsChatAndNativeImageExplicit() {
        assertThat(provider.getDefaultBaseUrl()).isEqualTo("https://api.minimax.io");
        assertThat(provider.getSupportedAdapterTypes()).containsExactly(
            AdapterType.MINIMAX_MESSAGES, AdapterType.MINIMAX_CHAT,
            AdapterType.MINIMAX_RESPONSES, AdapterType.MINIMAX_IMAGE);
        assertThat(provider.getSupportedFeatures()).containsExactly(
            ModelFeature.STREAMING, ModelFeature.VISION, ModelFeature.TOOL_CALL,
            ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING);
        assertThat(provider.getSupportedFeatures(AdapterType.MINIMAX_RESPONSES))
            .contains(ModelFeature.VISION);
        assertThat(provider.buildChatModel(provider(), "key", "MiniMax-M2.7"))
            .isInstanceOf(MiniMaxMessagesModel.class);
        assertThat(provider.buildChatModel(provider(), "key",
            new ProviderModelRef("MiniMax-M2.7", ModelType.LANGUAGE,
                AdapterType.MINIMAX_MESSAGES)))
            .isInstanceOf(MiniMaxMessagesModel.class);
        assertThat(provider.buildChatModel(provider(), "key",
            new ProviderModelRef("MiniMax-M2.7", ModelType.LANGUAGE, AdapterType.MINIMAX_CHAT)))
            .isInstanceOf(MiniMaxChatModel.class);
        assertThat(provider.buildChatModel(provider(), "key",
            new ProviderModelRef("MiniMax-M2.7", ModelType.LANGUAGE,
                AdapterType.MINIMAX_RESPONSES)))
            .isInstanceOf(MiniMaxResponsesModel.class);
        assertThat(provider.buildImageGenerationClient(provider(), "key", "image-01"))
            .isInstanceOf(MiniMaxImageGenerationClient.class);
    }

    @Test
    void messagesUsesCurrentAuthenticationAndReasoningDefaults() {
        var options = options(GenerateTextRequest.builder()
            .prompt("Explain")
            .maxOutputTokens(2048)
            .temperature(1d)
            .topP(0.95d)
            .build(), "MiniMax-M2.7");
        var body = messagesBody(options, new UserMessage("Explain"));

        assertThat(body).containsEntry("model", "MiniMax-M2.7")
            .containsEntry("max_tokens", 2048)
            .containsEntry("temperature", 1d)
            .containsEntry("top_p", 0.95d)
            .doesNotContainKey("thinking")
            .doesNotContainKey("_halo_minimax_reasoning_mode");

        var headers = new HttpHeaders();
        new MiniMaxMessagesProfile().applyHeaders(headers, options);
        assertThat(headers.getFirst("X-Api-Key")).isEqualTo("key");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2023-06-01");
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagesSupportsCurrentSamplingMediaAndIgnoredParameterSemantics() {
        var options = options(GenerateTextRequest.builder()
            .prompt("Cache")
            .providerOptions(Map.of("minimax", Map.of(
                "systemCacheControl", Map.of("type", "ephemeral"),
                "lastMessageCacheControl", Map.of("type", "ephemeral"))))
            .build(), "MiniMax-M2.7");
        var body = messagesBody(options, new SystemMessage("Rules"), new UserMessage("Cache"));

        var system = (List<Map<String, Object>>) body.get("system");
        assertThat(system).singleElement().satisfies(block -> assertThat(block)
            .containsEntry("type", "text")
            .containsEntry("cache_control", Map.of("type", "ephemeral")));
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getLast().get("content");
        assertThat(content).singleElement().satisfies(block -> assertThat(block)
            .containsEntry("cache_control", Map.of("type", "ephemeral")));

        var disabled = options(GenerateTextRequest.builder().prompt("No think").build(),
            "MiniMax-M2.7");
        assertThat(messagesBody(disabled, new UserMessage("No think")))
            .doesNotContainKey("thinking");

        var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1}).build();
        var video = Media.builder()
            .mimeType(org.springframework.util.MimeType.valueOf("video/mp4"))
            .data(URI.create("https://example.com/video.mp4"))
            .build();
        var mediaBody = messagesBody(options,
            UserMessage.builder().text("See").media(List.of(image, video)).build());
        var mediaMessages = (List<Map<String, Object>>) mediaBody.get("messages");
        var mediaBlocks = (List<Map<String, Object>>) mediaMessages.getFirst().get("content");
        assertThat(mediaBlocks).extracting(block -> block.get("type"))
            .containsExactly("text", "image", "video");
        assertThat((Map<String, Object>) mediaBlocks.get(1).get("source"))
            .containsEntry("type", "base64")
            .containsEntry("media_type", "image/png");
        assertThat((Map<String, Object>) mediaBlocks.get(2).get("source"))
            .containsEntry("type", "url")
            .containsEntry("url", "https://example.com/video.mp4");

        var invalidTemperature = options(GenerateTextRequest.builder().prompt("Hot")
            .temperature(2.01d).build(), "MiniMax-M2.7");
        assertThatThrownBy(() -> messagesBody(invalidTemperature, new UserMessage("Hot")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("temperature").hasMessageContaining("between 0.0 and 2.0");

        var supportedBoundaries = options.mutate()
            .temperature(0d)
            .topP(0d)
            .serviceTier("priority")
            .extraBody(Map.of(
                "top_k", 40,
                "stop_sequences", List.of("done"),
                "mcp_servers", List.of(Map.of("name", "ignored")),
                "context_management", Map.of("edits", List.of()),
                "container", "ignored"))
            .build();
        assertThat(messagesBody(supportedBoundaries, new UserMessage("Boundary")))
            .containsEntry("temperature", 0d)
            .containsEntry("top_p", 0d)
            .containsEntry("service_tier", "priority")
            .doesNotContainKeys("top_k", "stop_sequences", "mcp_servers",
                "context_management", "container");
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagesReplaysSignedThinkingBlocksExactlyForToolContinuation() {
        var reasoningBlock = Map.<String, Object>of(
            "type", "thinking", "thinking", "Inspect first", "signature", "signed-1");
        var assistant = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningProviderMetadata", Map.of("minimax", Map.of(
                "reasoningBlocks", List.of(reasoningBlock)))))
            .toolCalls(List.of(new AssistantMessage.ToolCall(
                "tool-1", "function", "search", "{\"q\":\"Halo\"}")))
            .build();
        var body = messagesBody(options(GenerateTextRequest.builder().prompt("Find")
            .build(), "MiniMax-M2.7"), new UserMessage("Find"), assistant);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getLast().get("content");

        assertThat(content.getFirst()).isEqualTo(reasoningBlock);
        assertThat(content.get(1)).containsEntry("type", "tool_use")
            .containsEntry("id", "tool-1")
            .containsEntry("name", "search")
            .containsEntry("input", Map.of("q", "Halo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatUsesCurrentTokenFieldAndPreservesReasoningDetails() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com")
            .model("MiniMax-M2.7")
            .maxTokens(1024)
            .build();
        var model = new MiniMaxChatModel(options, WebClient.builder());
        var reasoningDetails = List.of(Map.of(
            "type", "reasoning.text", "text", "Inspect first", "signature", "signed-1"));
        var assistant = AssistantMessage.builder().content("")
            .properties(Map.of("reasoningProviderMetadata", Map.of("minimax", Map.of(
                "reasoningDetails", reasoningDetails))))
            .build();
        var body = chatBody(model, options, new UserMessage("Continue"), assistant);
        var messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(body).containsEntry("max_completion_tokens", 1024)
            .containsEntry("reasoning_split", true)
            .doesNotContainKey("max_tokens");
        assertThat(messages.getLast()).containsEntry("reasoning_details", reasoningDetails)
            .doesNotContainKey("reasoning_content");

        var mediaUser = UserMessage.builder().text("Describe")
            .media(List.of(
                Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
                    .data(new byte[] {1}).build(),
                Media.builder().mimeType(org.springframework.util.MimeType.valueOf("video/mp4"))
                    .data(URI.create("https://example.com/video.mp4")).build()))
            .build();
        var mediaBody = chatBody(model, options, mediaUser);
        var mediaMessages = (List<Map<String, Object>>) mediaBody.get("messages");
        var mediaParts = (List<Map<String, Object>>) mediaMessages.getFirst().get("content");
        assertThat(mediaParts).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url");

        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", """
                {"id":"chat-1","model":"MiniMax-M2.7","choices":[{
                  "index":0,"finish_reason":"stop","message":{"role":"assistant",
                  "content":"Done","reasoning_details":[
                    {"type":"reasoning.text","text":"Inspect first","signature":"signed-1"}
                  ]}}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """, options, "diagnostic-1");
        assertThat(response.getResult().getOutput().getMetadata())
            .containsEntry("reasoningContent", "Inspect first")
            .containsEntry("reasoningDetails", reasoningDetails);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesMapsDocumentedImageAndVideoInputs() {
        var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1}).build();
        var video = Media.builder()
            .mimeType(org.springframework.util.MimeType.valueOf("video/mp4"))
            .data(URI.create("https://example.com/video.mp4"))
            .build();
        var options = options(GenerateTextRequest.builder().prompt("Describe").build(),
            "opaque-model").mutate()
            .toolContext("minimax-responses.messages", List.of(
                UserMessage.builder().text("Describe").media(List.of(image, video)).build()))
            .build();
        var model = new MiniMaxResponsesModel(options, WebClient.builder());
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);
        var input = (List<Map<String, Object>>) body.get("input");
        var content = (List<Map<String, Object>>) input.getFirst().get("content");

        assertThat(content.get(1))
            .containsEntry("type", "input_image")
            .containsEntry("image_url", "data:image/png;base64,AQ==");
        assertThat(content.get(2))
            .containsEntry("type", "input_video")
            .containsEntry("video_url", "https://example.com/video.mp4");
    }

    @Test
    void imageOptionsValidationAndApplicationErrorsAreProviderNative() {
        var client = new MiniMaxImageGenerationClient(new ImageGenerationClientOptions(
            "minimax", "https://api.minimax.io", "key", "image-01", null),
            WebClient.builder());
        var body = client.requestBody(GenerateImageRequest.builder()
            .prompt("Halo mascot")
            .n(9)
            .size("1024x768")
            .providerOptions(Map.of("minimax", Map.of(
                "prompt_optimizer", true, "aigc_watermark", false, "style", "anime")))
            .build());
        assertThat(body).containsEntry("prompt_optimizer", true)
            .containsEntry("aigc_watermark", false)
            .containsEntry("style", "anime")
            .containsEntry("width", 1024)
            .containsEntry("height", 768)
            .containsEntry("n", 9);

        assertThatThrownBy(() -> client.requestBody(GenerateImageRequest.builder()
            .prompt("Halo").n(10).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 9");
        assertThatThrownBy(() -> client.requestBody(GenerateImageRequest.builder()
            .prompt("Halo").size("513x1024").build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("divisible by 8");
        assertThatThrownBy(() -> client.imageResponse("""
            {"base_resp":{"status_code":1004,"status_msg":"invalid parameter"}}
            """, GenerateImageRequest.builder().prompt("Halo").build()))
            .isInstanceOf(ProviderHttpException.class)
            .hasMessageContaining("status=200");

        var partial = client.imageResponse("""
            {"data":{"image_urls":["https://example.com/image.png"]},
             "metadata":{"success_count":"1","failed_count":"2"},
             "base_resp":{"status_code":0,"status_msg":"success"}}
            """, GenerateImageRequest.builder().prompt("Halo").build());
        assertThat(partial.getWarnings()).singleElement().satisfies(warning -> {
            assertThat(warning.getCode()).isEqualTo("partial-generation");
            assertThat(warning.getProviderMetadata()).containsEntry("failedCount", 2);
        });
    }

    @Test
    void modelDiscoveryUsesTheDocumentedCatalogAndNarrowsCapabilities() throws Exception {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var bytes = """
                    {"object":"list","data":[
                      {"id":"current-language-model","object":"model","owned_by":"minimax"},
                      {"id":"future-language-model","object":"model","owned_by":"minimax"}]}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            StepVerifier.create(provider.discoverModels(provider(baseUrl), "key"))
                .assertNext(models -> {
                    assertThat(models).extracting(model -> model.modelId())
                        .containsExactly("current-language-model", "future-language-model");
                    assertThat(models).allSatisfy(model -> {
                        assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                        assertThat(model.adapterType()).isEqualTo(AdapterType.MINIMAX_MESSAGES);
                        assertThat(model.features()).containsExactlyInAnyOrderElementsOf(
                            provider.getSupportedFeatures(AdapterType.MINIMAX_MESSAGES));
                        assertThat(model.source()).isEqualTo(DiscoverySource.RULE);
                        assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.LOW);
                    });
                })
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer key");
        } finally {
            server.stop(0);
        }
        assertThat(provider.getDefaultParameterMappings().get(ModelParameter.REASONING))
            .satisfies(mapping -> {
                assertThat(mapping.mode()).isEqualTo(ModelParameterMappings.Mode.TEMPLATE);
                assertThat(mapping.template()).isEqualTo("reasoning.minimax");
            });
        assertThat(provider.getDefaultParameterMappings().get(ModelParameter.SEED).mode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
    }

    private ChatCompletionsOptions options(GenerateTextRequest request, String modelId) {
        return ((ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(request)).mutate()
            .baseUrl("https://example.com")
            .apiKey("key")
            .model(modelId)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> messagesBody(ChatCompletionsOptions options,
        Message... messages) {
        var model = new MiniMaxMessagesModel(options, WebClient.builder());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model, "requestBody",
            new Prompt(List.of(messages), options), options, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatBody(MiniMaxChatModel model, ChatCompletionsOptions options,
        Message... messages) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model, "requestBody",
            new Prompt(List.of(messages), options), options, false);
    }

    private AiProvider provider() {
        return provider(null);
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("minimax-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("minimax");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
