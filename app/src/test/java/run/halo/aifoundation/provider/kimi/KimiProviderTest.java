package run.halo.aifoundation.provider.kimi;

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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "kimi",
    officialDocumentation = "https://platform.kimi.com/docs/api/chat",
    retrievedAt = "2026-08-26"
)
class KimiProviderTest {

    private final KimiProvider provider = new KimiProvider();

    @Test
    void declaresDedicatedChatContractAndProviderNativeDefaults() {
        assertThat(provider.getSupportedAdapterTypes()).containsExactly(AdapterType.KIMI_CHAT);
        assertThat(provider.maxEmbeddingsPerCall()).isZero();
        assertThat(provider.supportsParallelCalls()).isFalse();
        assertThat(provider.buildChatModel(provider("https://example.com/v1"), "test-key",
            "kimi-k3")).isInstanceOf(KimiChatModel.class);
        assertThat(provider.getDefaultParameterMappings().get(ModelParameter.MAX_OUTPUT_TOKENS)
            .template()).isEqualTo("openai.max-completion-tokens");
        assertThat(provider.getDefaultParameterMappings().get(ModelParameter.SEED).mode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);

        var options = provider.languageModelProviderOptions(AdapterType.KIMI_CHAT);
        assertThat(options.reasoningControlOptions().enabledSupported()).isFalse();
        assertThat(options.reasoningControlOptions().supportedEfforts()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reasoningIsMappingDrivenAndCurrentTokenFieldIsProtocolWide() {
        var k3Options = options(GenerateTextRequest.builder()
            .prompt("Think")
            .maxOutputTokens(4096)
            .temperature(1d)
            .topP(0.95d)
            .build(), "kimi-k3");
        var k3Body = requestBody(k3Options, new UserMessage("Think"));

        assertThat(k3Body).containsEntry("max_completion_tokens", 4096)
            .doesNotContainKeys("max_tokens", "thinking", "reasoning_effort");

        var k26Options = options(GenerateTextRequest.builder()
            .prompt("Answer")
            .temperature(0.6d)
            .build(), "kimi-k2.6");
        var k26Body = requestBody(k26Options, new UserMessage("Answer"));
        assertThat(k26Body).doesNotContainKey("thinking");

        var explicitThinking = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("future-kimi-model")
            .extraBody(Map.of("thinking", Map.of("type", "enabled")))
            .build();
        assertThat(requestBody(explicitThinking, new UserMessage("Think")))
            .containsEntry("thinking", Map.of("type", "enabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void partialModeIsAnExplicitKimiOptionOnTheFinalAssistantPrefix() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("kimi-k2.6")
            .extraBody(Map.of("partial", true))
            .build();
        var body = requestBody(options, new UserMessage("Write Python"),
            new AssistantMessage("```python\n"));
        var messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(body).doesNotContainKey("partial");
        assertThat(messages.getLast()).containsEntry("role", "assistant")
            .containsEntry("content", "```python\n")
            .containsEntry("partial", true);

        var structured = options.mutate()
            .responseFormat(ChatCompletionsOptions.ResponseFormat.builder()
                .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
                .name("answer")
                .jsonSchema("{\"type\":\"object\"}")
                .build())
            .build();
        assertThatThrownBy(() -> requestBody(structured, new UserMessage("Write JSON"),
            new AssistantMessage("{")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Partial Mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeCacheStructuredOutputReasoningReplayAndToolConstraintsStayVisible() {
        var format = ChatCompletionsOptions.ResponseFormat.builder()
            .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
            .name("answer")
            .jsonSchema("{\"type\":\"object\",\"properties\":{"
                + "\"answer\":{\"type\":\"string\"}}}")
            .strict(true)
            .build();
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("kimi-k3")
            .responseFormat(format)
            .extraBody(Map.of("prompt_cache_key", "task-42",
                "safety_identifier", "hashed-user"))
            .build();
        var assistant = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", "Search first"))
            .toolCalls(List.of(new AssistantMessage.ToolCall(
                "call-1", "function", "search", "{\"q\":\"Halo\"}")))
            .build();
        var body = requestBody(options, new UserMessage("Find Halo"), assistant);
        var messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(body).containsEntry("prompt_cache_key", "task-42")
            .containsEntry("safety_identifier", "hashed-user");
        assertThat((Map<String, Object>) body.get("response_format"))
            .containsEntry("type", "json_schema");
        assertThat(messages.getLast()).containsEntry("reasoning_content", "Search first")
            .containsKey("tool_calls");

        var required = options.mutate().responseFormat(null).toolChoice("required")
            .model("kimi-k2.6").build();
        assertThat(requestBody(required, new UserMessage("Use a tool")))
            .containsEntry("tool_choice", "required");

        var duplicateTools = options.mutate().responseFormat(null)
            .extraBody(Map.of("tools", List.of(functionTool("search"), functionTool("search"))))
            .build();
        assertThatThrownBy(() -> requestBody(duplicateTools, new UserMessage("Search")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be unique");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mediaAcceptsOnlyDataOrMoonshotFileReferencesForImagesAndVideos() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("kimi-k2.6")
            .build();
        var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1, 2, 3}).build();
        var video = Media.builder().mimeType(MimeTypeUtils.parseMimeType("video/mp4"))
            .data(URI.create("ms://file-video-1")).build();
        var user = UserMessage.builder().text("Describe").media(image, video).build();
        var body = requestBody(options, user);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");

        assertThat(content).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url");
        assertThat((Map<String, Object>) content.get(1).get("image_url"))
            .extractingByKey("url").asString().startsWith("data:image/png;base64,");
        assertThat((Map<String, Object>) content.get(2).get("video_url"))
            .containsEntry("url", "ms://file-video-1");

        var external = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(URI.create("https://example.com/image.png")).build();
        assertThatThrownBy(() -> requestBody(options,
            UserMessage.builder().text("Describe").media(external).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("external URLs are not accepted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responseKeepsReasoningAndKimiCacheUsage() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("kimi-k3")
            .build();
        var model = new KimiChatModel(options, WebClient.builder());
        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", """
                {"id":"chat-1","model":"kimi-k3","choices":[{
                  "index":0,"finish_reason":"stop","message":{
                    "role":"assistant","content":"Done",
                    "reasoning_content":"Inspect the contract."}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,
                   "cached_tokens":6}}
                """, options, "diagnostic-1");

        assertThat(response.getResult().getOutput().getMetadata())
            .containsEntry("reasoningContent", "Inspect the contract.");
        assertThat((Map<String, Object>) response.getMetadata().getUsage().getNativeUsage())
            .containsEntry("cached_tokens", 6);
    }

    @Test
    void discoveryMapsImageVideoAndReasoningFlagsToTheKimiAdapter() throws Exception {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var bytes = """
                    {"data":[
                      {"id":"kimi-k3","context_length":1048576,
                       "supports_image_in":true,"supports_video_in":true,
                       "supports_reasoning":true},
                      {"id":"moonshot-v1-8k","supports_image_in":false,
                       "supports_video_in":false,"supports_reasoning":false}]}
                    """.getBytes(StandardCharsets.UTF_8);
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
                    assertThat(models).hasSize(2);
                    var k3 = models.getFirst();
                    assertThat(k3.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(k3.adapterType()).isEqualTo(AdapterType.KIMI_CHAT);
                    assertThat(k3.features()).containsExactlyInAnyOrder(ModelFeature.STREAMING,
                        ModelFeature.VISION, ModelFeature.REASONING);
                    assertThat(k3.capabilities().getLanguage().getImageInput()).isTrue();
                    assertThat(k3.capabilities().getLanguage().getFileInput()).isTrue();
                    assertThat(k3.capabilities().getLanguage().getReasoningHistory()).isTrue();
                    assertThat(k3.capabilities().getLanguage().getInputMediaTypes())
                        .containsExactly("image/*", "video/*");
                    assertThat(k3.capabilities().getLanguage().getInputSources())
                        .containsExactly(InputSource.DATA);
                    assertThat(k3.capabilitySources().getLanguage())
                        .isEqualTo(CapabilitySource.REMOTE);
                    assertThat(models.get(1).adapterType()).isEqualTo(AdapterType.KIMI_CHAT);
                    assertThat(models.get(1).capabilities()).isNull();
                })
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
        } finally {
            server.stop(0);
        }
    }

    private ChatCompletionsOptions options(GenerateTextRequest request, String modelId) {
        return ((ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(request)).mutate()
            .baseUrl("https://example.com/v1")
            .apiKey("test-key")
            .model(modelId)
            .build();
    }

    private Map<String, Object> functionTool(String name) {
        return Map.of("type", "function", "function", Map.of(
            "name", name,
            "description", "Search",
            "parameters", Map.of("type", "object")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new KimiChatModel(options, WebClient.builder());
        var prompt = new Prompt(List.of(messages), options);
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("kimi-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("kimi");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
