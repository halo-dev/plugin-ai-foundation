package run.halo.aifoundation.provider.mimo;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesWireCodec;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "mimo",
    officialDocumentation = "https://mimo.mi.com/docs/en-US/api/chat/responses; "
        + "https://mimo.mi.com/docs/en-US/api/chat/openai-api; "
        + "https://mimo.mi.com/docs/en-US/api/chat/anthropic-api; "
        + "https://mimo.mi.com/docs/en-US/api/model/list-models; "
        + "https://mimo.mi.com/docs/en-US/quick-start/model; "
        + "https://mimo.mi.com/docs/en-US/usage-guide/tool-calling/web-search",
    retrievedAt = "2026-08-27"
)
class MiMoProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MiMoProvider providerType = new MiMoProvider();

    @Test
    void usesResponsesByDefaultAndKeepsFullModalChatExplicit() {
        var provider = provider("https://example.com/v1");
        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(
            AdapterType.MIMO_RESPONSES, AdapterType.MIMO_CHAT, AdapterType.MIMO_MESSAGES);
        assertThat(providerType.buildChatModel(provider, "key", "mimo-v2.5"))
            .isInstanceOf(MiMoResponsesModel.class);
        assertThat(providerType.buildChatModel(provider, "key",
            new ProviderModelRef("mimo-v2.5", ModelType.LANGUAGE, AdapterType.MIMO_CHAT)))
            .isInstanceOf(MiMoChatModel.class);
        assertThat(providerType.buildChatModel(provider, "key",
            new ProviderModelRef("mimo-v2.5", ModelType.LANGUAGE, AdapterType.MIMO_MESSAGES)))
            .isInstanceOf(MiMoMessagesModel.class);
        assertThat(providerType.buildEmbeddingModel(provider, "key", "mimo-v2.5")).isNull();
        assertThat(providerType.getWebsiteUrl()).isEqualTo("https://mimo.mi.com/");
        assertThat(providerType.getDocumentationUrl()).contains("mimo.mi.com/docs/en-US");
        assertThat(getClass().getResource("/static/brands/xiaomimimo.png")).isNotNull();

        var responsesOptions = providerType.languageModelProviderOptions(
            AdapterType.MIMO_RESPONSES);
        var chatOptions = providerType.languageModelProviderOptions(AdapterType.MIMO_CHAT);
        assertThat(responsesOptions.reasoningControlOptions().enabledSupported()).isFalse();
        assertThat(chatOptions.reasoningControlOptions().enabledSupported()).isFalse();
    }

    @Test
    void messagesOmitsSamplingWhileThinkingAndPreservesItWhenDisabled() {
        var thinking = ChatCompletionsOptions.builder()
            .baseUrl("https://api.xiaomimimo.com")
            .apiKey("key")
            .model("opaque-model")
            .temperature(0.7)
            .topP(0.8)
            .extraBody(Map.of("thinking", Map.of("type", "enabled")))
            .build();
        assertThat(messagesBody(thinking, new UserMessage("Think")))
            .doesNotContainKeys("temperature", "top_p");

        var disabled = thinking.mutate()
            .extraBody(Map.of("thinking", Map.of("type", "disabled")))
            .build();
        assertThat(messagesBody(disabled, new UserMessage("Answer")))
            .containsEntry("temperature", 0.7)
            .containsEntry("top_p", 0.8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesMapsReasoningAndReplaysNativeReasoningItem() {
        var factory = providerType.languageModelProviderOptions().chatOptionsFactory();
        var options = (ChatCompletionsOptions) factory.build(GenerateTextRequest.builder()
            .prompt("Think")
            .build());
        options = options.mutate().baseUrl("https://example.com/v1")
            .model("mimo-v2.5-pro")
            .extraBody(Map.of("reasoning", Map.of("effort", "low"))).build();
        var body = responsesBody(options, new UserMessage("Think"));
        assertThat((Map<String, Object>) body.get("reasoning")).containsEntry("effort", "low");

        // Decode the provider response directly so the exact reasoning item can be reused in the
        // next request without a lossy reasoning-text reconstruction.
        var decoded = new ResponsesWireCodec(new MiMoResponsesProfile()).decodeResponse("""
            {"id":"resp-1","model":"mimo-v2.5-pro","status":"completed",
             "output":[
               {"id":"rs-1","type":"reasoning","summary":[],
                "content":[{"type":"reasoning_text","text":"inspect evidence"}]},
               {"id":"msg-1","type":"message","role":"assistant","status":"completed",
                "content":[{"type":"output_text","text":"done","annotations":[]}]}
             ],"usage":{"input_tokens":4,"output_tokens":3,"total_tokens":7}}
            """);
        assertThat(decoded.reasoning()).isEqualTo("inspect evidence");
        var providerItems = (List<Map<String, Object>>)
            decoded.providerMetadata().get("providerOutputItems");
        var assistant = AssistantMessage.builder().content("done").properties(Map.of(
            "reasoningContent", decoded.reasoning(),
            "reasoningProviderMetadata", Map.of("mimo", Map.of(
                "responsesReasoningItems", providerItems)))).build();
        var replay = responsesBody(options, assistant);
        var input = (List<Map<String, Object>>) replay.get("input");
        assertThat(input.getFirst()).containsEntry("id", "rs-1")
            .containsEntry("type", "reasoning");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesUsesMiMoReasoningStreamEventsAndNormalizesToolChoice() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("mimo-v2.5-pro")
            .build();
        var decoder = new ResponsesWireCodec(new MiMoResponsesProfile()).newStreamDecoder();
        var parts = decoder.accept("""
            {"type":"response.reasoning_text.delta","item_id":"rs-1",
             "delta":"verify"}
            """);
        assertThat(parts).singleElement().isInstanceOf(
            run.halo.aifoundation.provider.protocol.responses.ResponsesStreamPart.ReasoningDelta.class);

        var requiredTool = options.mutate().toolChoice("required").build();
        assertThat(responsesBody(requiredTool, new UserMessage("Use tool")))
            .doesNotContainKey("tool_choice");
        assertThat(chatBody(requiredTool, new UserMessage("Use tool")))
            .doesNotContainKey("tool_choice");

        var automaticTool = options.mutate().toolChoice("auto").build();
        assertThat(responsesBody(automaticTool, new UserMessage("Use tool")))
            .containsEntry("tool_choice", "auto");
        assertThat(chatBody(automaticTool, new UserMessage("Use tool")))
            .containsEntry("tool_choice", "auto");
    }

    @Test
    void thinkingModeOmitsUnsupportedSamplingParameters() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .model("mimo-v2.5")
            .temperature(0.2)
            .topP(0.4)
            .build();

        assertThat(responsesBody(options, new UserMessage("Think")))
            .doesNotContainKeys("temperature", "top_p");
        assertThat(chatBody(options, new UserMessage("Think")))
            .doesNotContainKeys("temperature", "top_p");

        var responsesWithoutThinking = options.mutate()
            .extraBody(Map.of("reasoning", Map.of("effort", "none")))
            .build();
        assertThat(responsesBody(responsesWithoutThinking, new UserMessage("Answer")))
            .containsEntry("temperature", 0.2)
            .containsEntry("top_p", 0.4);

        var chatWithoutThinking = options.mutate()
            .extraBody(Map.of("thinking", Map.of("type", "disabled")))
            .build();
        assertThat(chatBody(chatWithoutThinking, new UserMessage("Answer")))
            .containsEntry("temperature", 0.2)
            .containsEntry("top_p", 0.4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatMapsAudioVideoWebSearchAndCitations() {
        var providerOptions = Map.<String, Map<String, Object>>of("mimo", Map.of(
            "video", Map.of("fps", 4, "media_resolution", "max"),
            "builtinTools", List.of(Map.of(
                "type", "web_search", "max_keyword", 3, "force_search", true,
                "limit", 1)),
            "thinking", Map.of("type", "disabled")));
        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("Describe")
                .providerOptions(providerOptions)
                .build());
        options = options.mutate().baseUrl("https://example.com/v1")
            .model("mimo-v2.5").build();
        var user = UserMessage.builder().text("Describe")
            .media(List.of(
                new Media(MimeType.valueOf("audio/wav"), URI.create("https://example.com/a.wav")),
                new Media(MimeType.valueOf("video/mp4"), URI.create("https://example.com/v.mp4"))))
            .build();
        var body = chatBody(options, user);
        assertThat((Map<String, Object>) body.get("thinking"))
            .containsEntry("type", "disabled");
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");
        assertThat((Map<String, Object>) content.get(1).get("input_audio"))
            .containsEntry("data", "https://example.com/a.wav");
        assertThat(content.get(2)).containsEntry("fps", 4.0)
            .containsEntry("media_resolution", "max");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .anySatisfy(tool -> assertThat(tool).containsEntry("type", "web_search"));

        var model = new MiMoChatModel(options, WebClient.builder());
        var chunks = Flux.just("""
            {"id":"chat-1","model":"mimo-v2.5","choices":[{"index":0,
             "delta":{"content":"grounded","annotations":[{"type":"url_citation",
               "url":"https://example.com","title":"Source"}]},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,
               "prompt_tokens_details":{"cached_tokens":4},
               "completion_tokens_details":{"reasoning_tokens":2},
               "web_search_usage":{"tool_usage":3,"page_usage":2}}}
            """);
        var stream = (Flux<ProviderStreamPart>) ReflectionTestUtils.invokeMethod(model,
            "providerStreamParts", chunks, options);
        assertThat(stream.collectList().block()).anySatisfy(part -> {
            if (part instanceof ProviderStreamPart.ChatResponsePart responsePart) {
                assertThat(responsePart.response().getResult().getOutput().getMetadata())
                    .containsKey("annotations");
                var rawUsage = (Map<String, Object>) responsePart.response().getMetadata()
                    .getUsage().getNativeUsage();
                assertThat((Map<String, Object>) rawUsage.get("prompt_tokens_details"))
                    .containsEntry("cached_tokens", 4);
                assertThat((Map<String, Object>) rawUsage.get("web_search_usage"))
                    .containsEntry("tool_usage", 3);
            }
        });
    }

    @Test
    void adaptersRejectContractMismatchesBeforeNetwork() {
        var pro = ChatCompletionsOptions.builder().baseUrl("https://example.com/v1")
            .model("mimo-v2.5-pro").build();
        var image = UserMessage.builder().text("See")
            .media(List.of(new Media(MimeType.valueOf("image/png"),
                new ByteArrayResource(new byte[] {1, 2}))))
            .build();
        assertThat(chatBody(pro, image)).containsKey("messages");
        assertThat(responsesBody(pro, image)).containsKey("input");

        var chatEffort = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder().prompt("Think")
                .build());
        chatEffort = chatEffort.mutate().baseUrl("https://example.com/v1")
            .model("mimo-v2.5").build();
        var finalChatEffort = chatEffort;
        assertThat(chatBody(finalChatEffort, new UserMessage("Think")))
            .doesNotContainKeys("thinking", "reasoning_effort");
    }

    @Test
    void discoveryReturnsIdentifierOnlyCatalogAsLowConfidenceProviderDefaults() throws Exception {
        var capturedHeader = new AtomicReference<String>();
        var server = server();
        server.createContext("/v1/models", exchange -> {
            capturedHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, """
                {"object":"list","data":[
                  {"id":"mimo-v2.5","object":"model","owned_by":"xiaomi"},
                  {"id":"mimo-v2.5-pro","object":"model","owned_by":"xiaomi"},
                  {"id":"mimo-v3-preview","object":"model","owned_by":"xiaomi"},
                  {"id":"mimo-v2.5-asr","object":"model","owned_by":"xiaomi"},
                  {"id":"mimo-v2.5-tts","object":"model","owned_by":"xiaomi"}]}
                """);
        });
        server.start();
        try {
            StepVerifier.create(providerType.discoverModels(provider(baseUrl(server)), "sk-test"))
                .assertNext(models -> {
                    assertThat(models).hasSize(5);
                    assertThat(models).allSatisfy(model -> {
                        assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                        assertThat(model.adapterType()).isEqualTo(AdapterType.MIMO_RESPONSES);
                        assertThat(model.features()).containsExactlyInAnyOrderElementsOf(
                            providerType.getSupportedFeatures(AdapterType.MIMO_RESPONSES));
                        assertThat(model.source()).isEqualTo(DiscoverySource.RULE);
                        assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.LOW);
                    });
                })
                .verifyComplete();
            assertThat(capturedHeader.get()).isEqualTo("Bearer sk-test");
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responsesBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new MiMoResponsesModel(options, WebClient.builder());
        var key = (String) ReflectionTestUtils.getField(model, "messageContextKey");
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options.mutate().toolContext(key,
                List.of(messages)).build(), false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new MiMoChatModel(options, WebClient.builder());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(List.of(messages), options), options, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> messagesBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new MiMoMessagesModel(options, WebClient.builder());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(List.of(messages), options), options, false);
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
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
        metadata.setName("mimo-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("mimo");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
