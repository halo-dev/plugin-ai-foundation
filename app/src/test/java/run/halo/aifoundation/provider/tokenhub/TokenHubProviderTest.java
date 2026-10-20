package run.halo.aifoundation.provider.tokenhub;

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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "tokenhub",
    officialDocumentation = "https://cloud.tencent.com/document/product/1823/130079",
    retrievedAt = "2026-10-20"
)
class TokenHubProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TokenHubProvider provider = new TokenHubProvider();

    @Test
    void declaresChatAndEmbeddingAdaptersWithCurrentTokenDefaults() {
        var aiProvider = provider("https://example.com/v1");

        assertThat(provider.getProviderType()).isEqualTo("tokenhub");
        assertThat(provider.getDefaultBaseUrl()).isEqualTo("https://tokenhub.tencentmaas.com/v1");
        assertThat(provider.getSupportedAdapterTypes())
            .containsExactly(AdapterType.TOKENHUB_CHAT, AdapterType.TOKENHUB_EMBEDDING);
        assertThat(provider.getSupportedFeatures()).containsExactlyInAnyOrder(
            ModelFeature.STREAMING, ModelFeature.VISION, ModelFeature.TOOL_CALL,
            ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING);
        assertThat(provider.buildChatModel(aiProvider, "test-key", "hunyuan-turbos-latest"))
            .isInstanceOf(TokenHubChatModel.class);
        assertThat(provider.buildEmbeddingModel(aiProvider, "test-key",
            "kinfra-text-embedding-0.6b")).isInstanceOf(TokenHubEmbeddingModel.class);

        var options = provider.languageModelProviderOptions();
        assertThat(options.structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.JSON_SCHEMA);
        assertThat(options.nativeStrictToolSchemas()).isFalse();
        assertThat(options.seedSupported()).isTrue();
        assertThat(options.reasoningHistorySupported()).isTrue();
        assertThat(options.reasoningControlOptions().enabledSupported()).isFalse();
        assertThat(options.reasoningControlOptions().supportedEfforts()).isEmpty();

        var defaults = provider.getDefaultParameterMappings();
        assertThat(defaults.get(ModelParameter.MAX_OUTPUT_TOKENS).template())
            .isEqualTo("openai.max-completion-tokens");
        assertThat(defaults.get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.tokenhub");
        for (var parameter : List.of(ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.REPETITION_PENALTY, ModelParameter.DIMENSIONS)) {
            assertThat(defaults.get(parameter).mode())
                .as(parameter.name())
                .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
        }
        assertThat(provider.getDefaultParameterMappings(AdapterType.TOKENHUB_CHAT)
            .get(ModelParameter.REASONING).template()).isEqualTo("reasoning.tokenhub");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatRequestUsesCurrentTokenFieldsAndDocumentedOptions() {
        var options = options(GenerateTextRequest.builder()
            .prompt("Think")
            .maxOutputTokens(4096)
            .temperature(1d)
            .topP(0.9d)
            .seed(42)
            .build(), "hunyuan-turbos-latest");
        var body = requestBody(options, new UserMessage("Think"));

        assertThat(body).containsEntry("max_completion_tokens", 4096)
            .containsEntry("temperature", 1d)
            .containsEntry("top_p", 0.9d)
            .containsEntry("seed", 42)
            .doesNotContainKey("max_tokens");

        var thinking = options.mutate()
            .extraBody(Map.of("thinking", Map.of("type", "enabled"),
                "reasoning_effort", "medium"))
            .build();
        assertThat(requestBody(thinking, new UserMessage("Think")))
            .containsEntry("thinking", Map.of("type", "enabled"))
            .containsEntry("reasoning_effort", "medium");

        var format = ChatCompletionsOptions.ResponseFormat.builder()
            .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
            .name("answer")
            .jsonSchema("{\"type\":\"object\"}")
            .strict(true)
            .build();
        var structured = options.mutate().responseFormat(format).build();
        assertThat((Map<String, Object>) requestBody(structured, new UserMessage("JSON"))
            .get("response_format")).containsEntry("type", "json_schema");
    }

    @Test
    void chatRejectsValuesOutsideTheDocumentedContract() {
        var base = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("hunyuan-turbos-latest")
            .build();

        assertThatThrownBy(() -> requestBody(base.mutate().temperature(2.5d).build(),
            new UserMessage("Hot")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("temperature");
        assertThatThrownBy(() -> requestBody(base.mutate()
            .extraBody(Map.of("reasoning_effort", "xhigh")).build(),
            new UserMessage("Think")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reasoning_effort");
        assertThatThrownBy(() -> requestBody(base.mutate()
            .extraBody(Map.of("thinking", Map.of("type", "auto"))).build(),
            new UserMessage("Think")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("thinking.type");
        assertThatThrownBy(() -> requestBody(base.mutate()
            .extraBody(Map.of("thinking", Map.of("type", "enabled"))).n(2).build(),
            new UserMessage("Think")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("n=1");
        assertThatThrownBy(() -> requestBody(base.mutate()
            .stopSequences(List.of("a", "b", "c", "d", "e")).build(),
            new UserMessage("Stop")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("4 stop sequences");
        assertThatThrownBy(() -> requestBody(base.mutate().topLogprobs(5).build(),
            new UserMessage("Tokens")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires logprobs");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reasoningContentAndDetailsRoundTripThroughAssistantMessages() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("hunyuan-turbos-latest")
            .build();
        var model = new TokenHubChatModel(options, WebClient.builder());
        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", """
                {"id":"chat-1","model":"hunyuan-turbos-latest","choices":[{
                  "index":0,"finish_reason":"stop","message":{
                    "role":"assistant","content":"Done",
                    "reasoning_content":"Inspect the contract.",
                    "reasoning_details":[{"type":"reasoning.text","text":"Inspect",
                      "signature":"sig-1"}]}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """, options, "diagnostic-1");

        var metadata = response.getResult().getOutput().getMetadata();
        assertThat(metadata).containsEntry("reasoningContent", "Inspect the contract.")
            .containsKey("reasoningDetails");

        var assistant = AssistantMessage.builder()
            .content("Done")
            .properties(metadata)
            .build();
        var body = requestBody(options, new UserMessage("Find"), assistant);
        var messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages.getLast())
            .containsEntry("role", "assistant")
            .containsEntry("reasoning_content", "Inspect the contract.")
            .containsEntry("reasoning_details",
                List.of(Map.of("type", "reasoning.text", "text", "Inspect",
                    "signature", "sig-1")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mediaUsesDocumentedImageAndVideoParts() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("hunyuan-turbos-latest")
            .build();
        var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1, 2, 3}).build();
        var video = Media.builder().mimeType(MimeTypeUtils.parseMimeType("video/mp4"))
            .data(URI.create("https://example.com/clip.mp4")).build();
        var user = UserMessage.builder().text("Describe").media(image, video).build();
        var body = requestBody(options, user);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");

        assertThat(content).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "video_url");
        assertThat((Map<String, Object>) content.get(1).get("image_url"))
            .extractingByKey("url").asString().startsWith("data:image/png;base64,");
        assertThat((Map<String, Object>) content.get(2).get("video_url"))
            .containsEntry("url", "https://example.com/clip.mp4");

        var dataVideo = Media.builder().mimeType(MimeTypeUtils.parseMimeType("video/mp4"))
            .data(new byte[] {1, 2, 3}).build();
        assertThatThrownBy(() -> requestBody(options,
            UserMessage.builder().text("Describe").media(dataVideo).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTP(S) URL");

        var audio = Media.builder().mimeType(MimeTypeUtils.parseMimeType("audio/mpeg"))
            .data(new byte[] {1, 2, 3}).build();
        assertThatThrownBy(() -> requestBody(options,
            UserMessage.builder().text("Describe").media(audio).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("audio input");
    }

    @Test
    @SuppressWarnings("unchecked")
    void embeddingsUseTheOpenAiCompatibleEndpoint() throws Exception {
        var authorization = new AtomicReference<String>();
        var captured = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                captured.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                var response = """
                    {"object":"list","model":"kinfra-text-embedding-0.6b","data":[
                      {"index":1,"embedding":[0.3,0.4]},
                      {"index":0,"embedding":[0.1,0.2]}],
                     "usage":{"prompt_tokens":4,"total_tokens":4}}
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
            var model = new TokenHubEmbeddingModel(new TokenHubEmbeddingOptions(baseUrl,
                "test-key", "kinfra-text-embedding-0.6b", Map.of("X-Trace", "1"), null),
                WebClient.builder());
            var response = model.call(new ProviderEmbeddingRequest(
                List.of("Halo CMS", "AI Foundation"), List.of(), model.getOptions(), Map.of()));

            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(captured.get()).containsEntry("model", "kinfra-text-embedding-0.6b")
                .containsEntry("encoding_format", "float");
            assertThat((List<Object>) captured.get().get("input"))
                .containsExactly("Halo CMS", "AI Foundation");
            assertThat(response.getResults()).extracting(result -> result.getOutput()[0])
                .containsExactly(0.1f, 0.3f);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void embeddingOptionsRejectFieldsTokenHubDoesNotDocument() {
        var providerOptions = provider.embeddingModelProviderOptions();

        assertThatThrownBy(() -> providerOptions.buildOptions(EmbeddingRequest.builder()
            .inputs(List.of("Halo"))
            .dimensions(512)
            .build(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dimensions are fixed");
        assertThatThrownBy(() -> new TokenHubEmbeddingModel(new TokenHubEmbeddingOptions(
            "https://example.com/v1", "test-key", "kinfra-text-embedding-0.6b", Map.of(), null),
            WebClient.builder()).call(new ProviderEmbeddingRequest(List.of(),
                List.of(EmbeddingContent.text("Halo")), null, Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multimodal");
    }

    @Test
    void discoveryIsDisabledBecauseTheCatalogHasNoModelTypes() {
        StepVerifier.create(provider.discoverModels(provider("https://example.com/v1"),
                "test-key"))
            .assertNext(models -> assertThat(models).isEmpty())
            .verifyComplete();
    }

    private ChatCompletionsOptions options(GenerateTextRequest request, String modelId) {
        return ((ChatCompletionsOptions) provider.languageModelProviderOptions()
            .chatOptionsFactory().build(request)).mutate()
            .baseUrl("https://example.com/v1")
            .apiKey("test-key")
            .model(modelId)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new TokenHubChatModel(options, WebClient.builder());
        var prompt = new Prompt(List.of(messages), options);
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);
    }

    private AiProvider provider(String baseUrl) {
        var value = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("tokenhub-provider");
        value.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("tokenhub");
        spec.setBaseUrl(baseUrl);
        value.setSpec(spec);
        return value;
    }
}
