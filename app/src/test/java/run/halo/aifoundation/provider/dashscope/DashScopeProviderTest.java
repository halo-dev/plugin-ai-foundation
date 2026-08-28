package run.halo.aifoundation.provider.dashscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "dashscope",
    officialDocumentation = "https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions; "
        + "https://help.aliyun.com/en/model-studio/qwen-api-via-openai-responses; "
        + "https://help.aliyun.com/zh/model-studio/anthropic-api-messages; "
        + "https://help.aliyun.com/zh/model-studio/getting-started/models",
    retrievedAt = "2026-08-27"
)
class DashScopeProviderTest {

    private final DashScopeProvider provider = new DashScopeProvider();

    @Test
    void endpointResolverKeepsRegionalEndpointFamiliesIsolated() {
        var workspace = new DashScopeEndpointResolver(
            "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/");
        var nativeConfigured = new DashScopeEndpointResolver(
            "https://workspace.ap-southeast-1.maas.aliyuncs.com/api/v1");
        var messagesConfigured = new DashScopeEndpointResolver(
            "https://workspace.ap-northeast-1.maas.aliyuncs.com/apps/anthropic");

        assertThat(workspace.compatibleBaseUrl()).isEqualTo(
            "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");
        assertThat(workspace.nativeBaseUrl()).isEqualTo(
            "https://workspace.cn-beijing.maas.aliyuncs.com/api/v1");
        assertThat(workspace.compatibleApiBaseUrl()).isEqualTo(
            "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-api/v1");
        assertThat(workspace.messagesBaseUrl()).isEqualTo(
            "https://workspace.cn-beijing.maas.aliyuncs.com/apps/anthropic");
        assertThat(nativeConfigured.modelCatalogUrl()).isEqualTo(
            "https://workspace.ap-southeast-1.maas.aliyuncs.com/api/v1/models");
        assertThat(messagesConfigured.compatibleBaseUrl()).isEqualTo(
            "https://workspace.ap-northeast-1.maas.aliyuncs.com/compatible-mode/v1");
    }

    @Test
    void exposesChatResponsesAndMessagesAndUsesExplicitNativeReasoning() {
        var aiProvider = provider("https://example.com/compatible-mode/v1");
        assertThat(provider.getSupportedAdapterTypes()).startsWith(
            AdapterType.DASHSCOPE_CHAT, AdapterType.DASHSCOPE_RESPONSES);
        assertThat(provider.buildChatModel(aiProvider, "test-key", new ProviderModelRef(
            "qwen3.8-max", ModelType.LANGUAGE, AdapterType.DASHSCOPE_RESPONSES)))
            .isInstanceOf(DashScopeResponsesModel.class);
        assertThat(provider.buildChatModel(aiProvider, "test-key", new ProviderModelRef(
            "opaque-model", ModelType.LANGUAGE, AdapterType.DASHSCOPE_MESSAGES)))
            .isInstanceOf(DashScopeMessagesModel.class);
        assertThat(provider.getSupportedFeatures(AdapterType.DASHSCOPE_MESSAGES))
            .contains(ModelFeature.VISION)
            .doesNotContain(ModelFeature.AUDIO_INPUT);

        var enabled = (ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder().prompt("Think").build());
        enabled = enabled.mutate().extraBody(Map.of("enable_thinking", true)).build();
        var disabled = (ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder().prompt("Answer").build());
        disabled = disabled.mutate().extraBody(Map.of("enable_thinking", false)).build();

        assertThat(enabled.getExtraBody()).containsEntry("enable_thinking", true);
        assertThat(disabled.getExtraBody()).containsEntry("enable_thinking", false);

        var effort = (ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(
                GenerateTextRequest.builder().prompt("Think harder").build());
        effort = effort.mutate().extraBody(Map.of("reasoning_effort", "high")).build();
        assertThat(effort.getExtraBody()).containsEntry("reasoning_effort", "high");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesMapsReasoningAndMergesOfficialBuiltInTools() {
        var builtin = Map.<String, Object>of("type", "web_search");
        var generated = (ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("Search and reason")
                .build());
        var options = generated.mutate()
            .baseUrl("https://example.com/compatible-mode/v1")
            .model("qwen3.8-max")
            .extraBody(Map.of(
                "builtinTools", List.of(builtin),
                "reasoning", Map.of("effort", "medium")))
            .toolContext("dashscope-responses.messages", List.of(new UserMessage("Search")))
            .build();
        var model = new DashScopeResponsesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);

        assertThat(body).containsEntry("store", false)
            .containsEntry("reasoning", Map.of("effort", "medium"))
            .doesNotContainKey("builtinTools");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .containsExactly(builtin);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatProfileReplaysReasoningHistoryAndRequestsStreamUsage() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/compatible-mode/v1")
            .apiKey("test-key")
            .model("qwen3.8-max")
            .maxTokens(2048)
            .build();
        var model = new DashScopeChatModel(options, WebClient.builder());
        var assistant = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", "Need current weather"))
            .toolCalls(List.of(new AssistantMessage.ToolCall(
                "call_1", "function", "weather", "{}")))
            .build();
        var prompt = new Prompt(List.of(new UserMessage("Weather?"), assistant), options);

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, true);
        var messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(messages.get(1)).containsEntry("reasoning_content", "Need current weather");
        assertThat(body).containsEntry("stream_options", Map.of("include_usage", true))
            .containsEntry("max_completion_tokens", 2048)
            .doesNotContainKey("max_tokens");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatUsesDashScopeMultimodalContentShapes() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/compatible-mode/v1")
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
        var model = new DashScopeChatModel(options, WebClient.builder());
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(List.of(user), options), options, false);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var parts = (List<Map<String, Object>>) messages.getFirst().get("content");

        assertThat(parts).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url", "input_audio");
        assertThat((Map<String, Object>) parts.get(3).get("input_audio"))
            .containsEntry("data", "https://example.com/audio.wav")
            .doesNotContainKey("format");
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagesUsesDocumentedImageVideoAndSamplingContract() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/apps/anthropic")
            .model("opaque-model")
            .temperature(1.99)
            .build();
        var user = UserMessage.builder().text("Describe")
            .media(List.of(
                Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
                    .data(new byte[] {1, 2, 3}).build(),
                Media.builder().mimeType(MimeType.valueOf("video/mp4"))
                    .data(URI.create("https://example.com/video.mp4")).build()))
            .build();
        var model = new DashScopeMessagesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(user), options, false);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var parts = (List<Map<String, Object>>) messages.getFirst().get("content");

        assertThat(parts).extracting(part -> part.get("type"))
            .containsExactly("text", "image", "video");
        assertThat((Map<String, Object>) parts.get(1).get("source"))
            .containsEntry("type", "base64")
            .containsEntry("data", "AQID");
        assertThat((Map<String, Object>) parts.get(2).get("source"))
            .containsEntry("type", "url")
            .containsEntry("url", "https://example.com/video.mp4");

        var invalid = options.mutate().temperature(2d).build();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(user), invalid, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("less than 2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagesMapsNativeJsonSchemaOutputConfiguration() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")))))
            .build();
        var options = (ChatCompletionsOptions) provider
            .languageModelProviderOptions(AdapterType.DASHSCOPE_MESSAGES)
            .structuredOutputChatOptionsFactory().build(request);
        options = options.mutate()
            .baseUrl("https://example.com/apps/anthropic")
            .model("opaque-model")
            .build();
        var model = new DashScopeMessagesModel(options, WebClient.builder());
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(new UserMessage("Generate JSON")), options, false);
        var outputConfig = (Map<String, Object>) body.get("output_config");
        var format = (Map<String, Object>) outputConfig.get("format");

        assertThat(format).containsEntry("type", "json_schema");
        assertThat((Map<String, Object>) format.get("schema"))
            .containsEntry("type", "object");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeEmbeddingMapsDimensionsTextRoleAndSparseMetadataInInputOrder() {
        var options = new DashScopeEmbeddingOptions(
            "https://example.com/api/v1", "test-key", "text-embedding-v4", 256,
            DashScopeEmbeddingOptions.TextType.QUERY,
            DashScopeEmbeddingOptions.OutputType.DENSE_AND_SPARSE, Map.of(), null);
        var model = new DashScopeEmbeddingModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", List.of("first", "second"), options);
        var parameters = (Map<String, Object>) body.get("parameters");
        assertThat(body).containsEntry("model", "text-embedding-v4")
            .containsEntry("input", Map.of("texts", List.of("first", "second")));
        assertThat(parameters).containsEntry("dimension", 256)
            .containsEntry("text_type", "query")
            .containsEntry("output_type", "dense&sparse");

        var response = (org.springframework.ai.embedding.EmbeddingResponse)
            ReflectionTestUtils.invokeMethod(model, "embeddingResponse", """
                {
                  "request_id":"embedding-1",
                  "output":{"embeddings":[
                    {"text_index":1,"embedding":[0.3,0.4],
                     "sparse_embedding":[{"index":4,"value":0.8,"token":"second"}]},
                    {"text_index":0,"embedding":[0.1,0.2],
                     "sparse_embedding":[{"index":2,"value":0.7,"token":"first"}]}
                  ]},
                  "usage":{"total_tokens":9}
                }
                """, options.model());

        assertThat(response.getResults()).extracting(result -> result.getIndex())
            .containsExactly(0, 1);
        assertThat(response.getResults().getFirst().getOutput()).containsExactly(0.1f, 0.2f);
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(9);
        assertThat((List<Map<String, Object>>) response.getMetadata().get("sparseEmbeddings"))
            .extracting(value -> value.get("textIndex"))
            .containsExactly(0, 1);
    }

    @Test
    void nativeEmbeddingRejectsSparseOnlyOutputInsteadOfReturningFakeDenseVectors() {
        var options = new DashScopeEmbeddingOptions(
            "https://example.com/api/v1", "test-key", "text-embedding-v4", null, null,
            DashScopeEmbeddingOptions.OutputType.SPARSE, Map.of(), null);
        var model = new DashScopeEmbeddingModel(options, WebClient.builder());

        assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("text"), options)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sparse-only");
    }

    @Test
    void modelCatalogMetadataDrivesAdapterAndUniqueFeatureDiscovery() {
        var discovered = (run.halo.aifoundation.provider.support.DiscoveredModel)
            ReflectionTestUtils.invokeMethod(provider, "discoveredModel", Map.of(
                "model", "qwen3-vl-plus",
                "name", "Qwen3 VL Plus",
                "capabilities", List.of("TG", "VU"),
                "features", List.of("Reasoning", "FunctionCalling", "structured-outputs")
            ));

        assertThat(discovered.modelType()).isEqualTo(ModelType.LANGUAGE);
        assertThat(discovered.adapterType()).isEqualTo(AdapterType.DASHSCOPE_CHAT);
        assertThat(discovered.features()).containsExactlyInAnyOrder(
            ModelFeature.STREAMING,
            ModelFeature.VISION,
            ModelFeature.REASONING,
            ModelFeature.TOOL_CALL,
            ModelFeature.STRUCTURED_OUTPUT
        );
    }

    @Test
    void modelCatalogUnderstandsDocumentedCapabilityCodes() {
        var textEmbedding = discoveredModel("TR");
        var multimodalEmbedding = discoveredModel("ME");
        var imageGeneration = discoveredModel("IG");

        assertThat(textEmbedding.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(multimodalEmbedding.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(imageGeneration.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
    }

    @Test
    void modelCatalogUsesNativeEndpointAndReadsEveryPage() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v1/models", exchange -> {
            try (exchange) {
                requests.add(exchange.getRequestURI().getRawQuery() + "|"
                    + exchange.getRequestHeaders().getFirst("Authorization"));
                var page = exchange.getRequestURI().getRawQuery().contains("page_no=2") ? 2 : 1;
                var body = page == 1 ? """
                    {"output":{"total":101,"page_no":1,"page_size":100,"models":[
                      {"model":"qwen3.8-max","name":"Qwen Max",
                       "capabilities":["TG"],"features":["Reasoning"]}
                    ]}}
                    """ : """
                    {"output":{"total":101,"page_no":2,"page_size":100,"models":[
                      {"model":"text-embedding-v4","name":"Embedding V4",
                       "capabilities":["TR"],"features":[]}
                    ]}}
                    """;
                var bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            StepVerifier.create(provider.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);
                    assertThat(models.get(0).adapterType()).isEqualTo(AdapterType.DASHSCOPE_CHAT);
                    assertThat(models.get(1).adapterType())
                        .isEqualTo(AdapterType.DASHSCOPE_EMBEDDING);
                })
                .verifyComplete();
            assertThat(requests).containsExactly(
                "page_no=1&page_size=100|Bearer sk-test",
                "page_no=2&page_size=100|Bearer sk-test"
            );
        } finally {
            server.stop(0);
        }
    }

    private run.halo.aifoundation.provider.support.DiscoveredModel discoveredModel(
        String capability) {
        return ReflectionTestUtils.invokeMethod(provider, "discoveredModel", Map.of(
            "model", "future-model", "name", "Future Model",
            "capabilities", List.of(capability), "features", List.of()));
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("dashscope-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("dashscope");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
