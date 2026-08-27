package run.halo.aifoundation.provider.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
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
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "openrouter",
    officialDocumentation = "https://openrouter.ai/docs/api/api-reference/chat/"
        + "create-a-chat-completion; https://openrouter.ai/docs/guides/routing/"
        + "provider-selection; https://openrouter.ai/docs/guides/overview/multimodal/"
        + "image-generation; https://openrouter.ai/docs/api/api-reference/responses/"
        + "create-responses; https://openrouter.ai/docs/api/api-reference/anthropic-messages/"
        + "create-a-message; https://openrouter.ai/docs/api/reference/embeddings",
    retrievedAt = "2026-08-27"
)
class OpenRouterProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OpenRouterProvider providerType = new OpenRouterProvider();

    @Test
    void declaresDedicatedDomainClientsAndUsesExplicitNativeReasoning() {
        var provider = provider("https://example.com/api/v1");

        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(
            AdapterType.OPENROUTER_CHAT, AdapterType.OPENROUTER_RESPONSES,
            AdapterType.OPENROUTER_MESSAGES, AdapterType.OPENROUTER_EMBEDDING, AdapterType.RERANK,
            AdapterType.OPENROUTER_IMAGE);
        assertThat(providerType.buildChatModel(provider, "key", "openai/gpt-5"))
            .isInstanceOf(OpenRouterChatModel.class);
        var responses = providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "openai/gpt-5", ModelType.LANGUAGE, AdapterType.OPENROUTER_RESPONSES));
        assertThat(responses).isInstanceOf(OpenRouterResponsesModel.class);
        assertThat(((OpenRouterResponsesModel) responses).getOptions().getCustomHeaders())
            .containsEntry("X-OpenRouter-Metadata", "enabled");
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "anthropic/claude", ModelType.LANGUAGE, AdapterType.OPENROUTER_MESSAGES)))
            .isInstanceOf(OpenRouterMessagesModel.class);
        assertThat(providerType.getSupportedFeatures(AdapterType.OPENROUTER_MESSAGES))
            .contains(ModelFeature.VISION)
            .doesNotContain(ModelFeature.AUDIO_INPUT);
        assertThat(providerType.languageModelProviderOptions(AdapterType.OPENROUTER_MESSAGES)
            .nativeStrictToolSchemas()).isFalse();
        assertThat(providerType.buildEmbeddingModel(provider, "key", "openai/embed"))
            .isInstanceOf(OpenRouterEmbeddingModel.class);
        assertThat(providerType.buildRerankingClient(provider, "key", "cohere/rerank"))
            .isInstanceOf(OpenRouterRerankingClient.class);
        assertThat(providerType.buildImageGenerationClient(provider, "key", "openai/image"))
            .isInstanceOf(OpenRouterImageGenerationClient.class);

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("Think")
                .providerOptions(Map.of("openrouter", Map.of(
                    "reasoning", Map.of("effort", "high"),
                    "models", List.of("anthropic/claude-sonnet", "google/gemini-pro"),
                    "provider", Map.of("order", List.of("anthropic", "google"),
                        "allow_fallbacks", false, "require_parameters", true, "zdr", true),
                    "plugins", List.of(Map.of("id", "response-healing")))))
                .build());
        var body = requestBody(options, new UserMessage("Think"));

        assertThat(body)
            .containsEntry("models", List.of("anthropic/claude-sonnet", "google/gemini-pro"))
            .containsEntry("usage", Map.of("include", true));
        assertThat(castMap(body.get("reasoning"))).containsEntry("effort", "high");
        assertThat(castMap(body.get("provider")))
            .containsEntry("allow_fallbacks", false)
            .containsEntry("require_parameters", true)
            .containsEntry("zdr", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesMapsRouterOptionsBuiltInToolsAndReasoningReplay() {
        var builtin = Map.<String, Object>of("type", "web_search");
        var generated = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder()
                .prompt("Research")
                .providerOptions(Map.of("openrouter", Map.of(
                    "reasoning", Map.of("enabled", true, "effort", "high"),
                    "models", List.of("openai/gpt-5", "anthropic/claude-sonnet"),
                    "provider", Map.of("order", List.of("openai", "anthropic"),
                        "zdr", true),
                    "plugins", List.of(Map.of("id", "web")),
                    "serverTools", List.of(builtin))))
                .build());
        var options = generated.mutate()
            .baseUrl("https://example.com/api/v1")
            .model("openai/gpt-5")
            .toolContext("openrouter-responses.messages", List.of(new UserMessage("Research")))
            .build();
        var model = new OpenRouterResponsesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);

        assertThat(body).containsEntry("store", false)
            .containsEntry("models", List.of("openai/gpt-5", "anthropic/claude-sonnet"))
            .doesNotContainKey("serverTools");
        assertThat(castMap(body.get("reasoning")))
            .containsEntry("enabled", true)
            .containsEntry("effort", "high");
        assertThat((List<Map<String, Object>>) body.get("tools")).containsExactly(builtin);

        var response = new run.halo.aifoundation.provider.protocol.responses.ResponsesWireCodec(
            new OpenRouterResponsesProfile()).decodeResponse("""
                {"id":"resp-1","model":"openai/gpt-5","status":"completed",
                 "output":[
                   {"id":"rs-1","type":"reasoning","summary":[],
                    "content":[{"type":"reasoning_text","text":"check sources"}]},
                   {"id":"msg-1","type":"message","role":"assistant",
                    "content":[{"type":"output_text","text":"done"}]}],
                 "usage":{"input_tokens":2,"output_tokens":2,"total_tokens":4},
                 "openrouter_metadata":{"strategy":"fallback"}}
                """);
        var reasoningItem = Map.<String, Object>of(
            "id", "rs-1",
            "type", "reasoning",
            "summary", List.of(),
            "content", List.of(Map.of("type", "reasoning_text",
                "text", "check sources")));
        var assistant = AssistantMessage.builder().content("done").properties(Map.of(
            "reasoningContent", response.reasoning(),
            "reasoningProviderMetadata", Map.of("openrouter", Map.of(
                "responsesReasoningItems", List.of(reasoningItem))))).build();
        var responseOptions = options.mutate().toolContext("openrouter-responses.messages",
            List.of(assistant)).build();
        var replay = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", responseOptions, false);
        var replayInput = (List<Map<String, Object>>) replay.get("input");
        assertThat(replayInput.getFirst()).containsEntry("type", "reasoning");
        assertThat(response.providerMetadata()).containsKey("openrouter");
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
        var options = (ChatCompletionsOptions) providerType
            .languageModelProviderOptions(AdapterType.OPENROUTER_MESSAGES)
            .structuredOutputChatOptionsFactory().build(request);
        options = options.mutate()
            .baseUrl("https://example.com/api/v1")
            .model("opaque-model")
            .build();
        var model = new OpenRouterMessagesModel(options, WebClient.builder());
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
    void preservesReasoningDetailsAnnotationsUsageAndUpstreamProviderForReplay() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/api/v1")
            .model("anthropic/claude-sonnet")
            .build();
        var model = new OpenRouterChatModel(options, WebClient.builder());
        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", """
                {"id":"gen-1","model":"anthropic/claude-sonnet","provider":"Anthropic",
                 "choices":[{"index":0,"finish_reason":"tool_calls","message":{
                   "role":"assistant","content":"","reasoning":"Inspect first",
                   "reasoning_details":[
                     {"type":"reasoning.text","format":"anthropic-claude-v1",
                      "text":"Inspect first","signature":"signed-1","id":"r-1"},
                     {"type":"reasoning.encrypted","data":"cipher","id":"r-2"}],
                   "annotations":[{"type":"file","file":{"hash":"h","name":"a.pdf"}}],
                   "tool_calls":[{"id":"call-1","type":"function",
                     "function":{"name":"lookup","arguments":"{}"}}]}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,
                   "prompt_tokens_details":{"cached_tokens":4},
                   "completion_tokens_details":{"reasoning_tokens":3},"cost":0.002}}
                """, options, "diagnostic-1");

        var output = response.getResult().getOutput();
        assertThat(output.getMetadata()).containsEntry("reasoningContent", "Inspect first")
            .containsKey("openRouterReasoningMetadata")
            .containsKey("annotations");
        assertThat(castMap(response.getMetadata().get("openrouter")))
            .containsEntry("provider", "Anthropic");
        assertThat(castMap(response.getMetadata().getUsage().getNativeUsage()))
            .containsEntry("cost", 0.002);

        var replayMetadata = Map.of("reasoningContent", "Inspect first",
            "reasoningProviderMetadata", Map.of("openrouter", Map.of(
                "openRouterReasoningMetadata",
                output.getMetadata().get("openRouterReasoningMetadata"))));
        var replay = AssistantMessage.builder()
            .content("")
            .properties(replayMetadata)
            .toolCalls(output.getToolCalls())
            .build();
        var replayBody = requestBody(options, new UserMessage("Continue"), replay);
        var messages = (List<Map<String, Object>>) replayBody.get("messages");
        assertThat(messages.getLast()).containsKeys("reasoning", "reasoning_details")
            .doesNotContainKey("reasoning_content");
        assertThat((List<?>) messages.getLast().get("reasoning_details")).hasSize(2);
    }

    @Test
    void rejectsUnsignedSignedReasoningAndInvalidRoutingTypesBeforeNetworkIo() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/api/v1")
            .model("anthropic/claude-sonnet")
            .extraBody(Map.of("provider", Map.of("zdr", "yes")))
            .build();
        assertThatThrownBy(() -> requestBody(options, new UserMessage("Hello")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider.zdr must be a boolean");

        var invalidDetails = List.of(Map.of("type", "reasoning.text",
            "format", "anthropic-claude-v1", "text", "unsigned", "id", "bad"));
        var assistant = AssistantMessage.builder().content("").properties(Map.of(
            "reasoningContent", "unsigned",
            "reasoningProviderMetadata", Map.of("openrouter", Map.of(
                "openRouterReasoningMetadata", Map.of("reasoningDetails", invalidDetails)))))
            .build();
        var validOptions = options.mutate().extraBody(Map.of()).build();
        var body = requestBody(validOptions, assistant);
        var messages = (List<?>) body.get("messages");
        assertThat(castMap(messages.getFirst())).containsEntry("reasoning_details", List.of())
            .doesNotContainKey("reasoning");
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamKeepsReasoningMetadataAndNativeToolInputLifecycle() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/api/v1")
            .model("anthropic/claude-sonnet")
            .build();
        var model = new OpenRouterChatModel(options, WebClient.builder());
        var chunks = Flux.just(
            """
                {"id":"gen-1","model":"anthropic/claude-sonnet","provider":"Anthropic",
                 "choices":[{"index":0,"delta":{"role":"assistant",
                   "reasoning_details":[{"type":"reasoning.text","text":"Check",
                     "signature":"sig","id":"r-1"}]}}]}
                """,
            """
                {"id":"gen-1","model":"anthropic/claude-sonnet","choices":[{"index":0,
                 "delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function",
                   "function":{"name":"lookup","arguments":"{\\\"q\\\""}}]}}]}
                """,
            """
                {"id":"gen-1","model":"anthropic/claude-sonnet","choices":[{"index":0,
                 "delta":{"tool_calls":[{"index":0,"function":{"arguments":":\\\"Halo\\\"}"}}]},
                 "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6,
                   "cost":0.001}}
                """);
        var stream = (Flux<ProviderStreamPart>) ReflectionTestUtils.invokeMethod(model,
            "providerStreamParts", chunks, options);
        var parts = stream.collectList().block();

        assertThat(parts).contains(
            new ProviderStreamPart.ToolInputStartPart(0, "call-1", "lookup"),
            new ProviderStreamPart.ToolInputDeltaPart(0, "{\"q\""),
            new ProviderStreamPart.ToolInputDeltaPart(0, ":\"Halo\"}"),
            new ProviderStreamPart.ToolInputEndPart(0));
        var responses = parts.stream()
            .filter(ProviderStreamPart.ChatResponsePart.class::isInstance)
            .map(ProviderStreamPart.ChatResponsePart.class::cast)
            .map(ProviderStreamPart.ChatResponsePart::response)
            .toList();
        assertThat(responses).anySatisfy(response -> assertThat(
            response.getResult().getOutput().getMetadata())
            .containsKey("openRouterReasoningMetadata"));
        assertThat(responses).anySatisfy(response -> {
            if (response.getMetadata().getUsage() != null) {
                assertThat(castMap(response.getMetadata().getUsage().getNativeUsage()))
                    .containsEntry("cost", 0.001);
            }
        });
    }

    @Test
    void embeddingAndMultimodalRerankUseDedicatedRouterContracts() throws Exception {
        var embeddingBody = new AtomicReference<Map<String, Object>>();
        var rerankBody = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/api/v1/embeddings", exchange -> {
            embeddingBody.set(readBody(exchange));
            respond(exchange, """
                {"id":"emb-1","model":"openai/text-embedding-3-small","provider":"OpenAI",
                 "data":[{"index":0,"embedding":[0.1,0.2]}],
                 "usage":{"prompt_tokens":3,"total_tokens":3,"cost":0.0001}}
                """);
        });
        server.createContext("/api/v1/rerank", exchange -> {
            rerankBody.set(readBody(exchange));
            respond(exchange, """
                {"id":"rerank-1","model":"nvidia/rerank","provider":"NVIDIA",
                 "results":[{"index":0,"relevance_score":0.98,
                   "document":{"text":"Halo"}}],
                 "usage":{"search_units":1,"total_tokens":7}}
                """);
        });
        server.start();
        try {
            var baseUrl = baseUrl(server);
            var embeddingModel = providerType.buildEmbeddingModel(provider(baseUrl), "key",
                "openai/text-embedding-3-small");
            var embeddingOptions = providerType.embeddingModelProviderOptions().buildOptions(
                EmbeddingRequest.builder().inputs(List.of("Halo")).dimensions(256)
                    .providerOptions(Map.of("openrouter", Map.of(
                        "input_type", "search_document",
                        "provider", Map.of("only", List.of("openai")))))
                    .build(), new java.util.ArrayList<>());
            var embedding = ((ProviderEmbeddingModel) embeddingModel).call(
                new ProviderEmbeddingRequest(List.of(), List.of(
                    EmbeddingContent.text("Halo"),
                    EmbeddingContent.image(DataContent.data(
                        new byte[] {1, 2, 3}, "image/png"))),
                    embeddingOptions, Map.of()));
            assertThat(embedding.getResult().getOutput()).containsExactly(0.1f, 0.2f);
            assertThat(embeddingBody.get()).containsEntry("dimensions", 256)
                .containsEntry("input_type", "search_document")
                .containsEntry("provider", Map.of("only", List.of("openai")));
            var embeddingInput = (List<?>) embeddingBody.get().get("input");
            assertThat(castMap(embeddingInput.getFirst())).containsKey("content");
            var content = (List<?>) castMap(embeddingInput.getFirst()).get("content");
            assertThat(castMap(content.getFirst()))
                .containsEntry("type", "text")
                .containsEntry("text", "Halo");
            assertThat(castMap(content.get(1)))
                .containsEntry("type", "image_url");
            assertThat((Object) embedding.getMetadata().get("provider")).isEqualTo("OpenAI");
            assertThat((Object) embedding.getMetadata().get("cost")).isEqualTo(0.0001);

            var reranker = providerType.buildRerankingClient(provider(baseUrl), "key",
                "nvidia/rerank");
            var rerank = reranker.rerank(RerankRequest.builder()
                .query("CMS")
                .documents(List.of(RerankDocument.builder().text("Halo")
                    .image(DataContent.url("https://example.com/halo.png", "image/png")).build()))
                .topN(1)
                .providerOptions(Map.of("openrouter", Map.of(
                    "provider", Map.of("zdr", true, "allow_fallbacks", false))))
                .build()).block();
            assertThat(rerank.getProviderMetadata()).containsEntry("provider", "NVIDIA")
                .containsKey("usage");
            var documents = (List<?>) rerankBody.get().get("documents");
            assertThat(castMap(documents.getFirst()))
                .containsEntry("text", "Halo")
                .containsEntry("image", "https://example.com/halo.png");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void imageAndDiscoveryUseCurrentDedicatedEndpointsAndRemoteCapabilities() throws Exception {
        var imageBody = new AtomicReference<Map<String, Object>>();
        var server = server();
        server.createContext("/api/v1/images", exchange -> {
            imageBody.set(readBody(exchange));
            respond(exchange, """
                {"id":"img-1","model":"openai/gpt-image-2","created":1,
                 "data":[{"b64_json":"YWJj","media_type":"image/webp"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":4,"total_tokens":5,
                   "cost":0.04}}
                """);
        });
        server.createContext("/api/v1/embeddings/models", exchange -> respond(exchange,
            "{\"data\":[{\"id\":\"openai/embed\",\"name\":\"Embed\"}]}"));
        server.createContext("/api/v1/images/models", exchange -> respond(exchange, """
            {"data":[{"id":"openai/image","name":"Image",
              "architecture":{"input_modalities":["text","image"],
                "output_modalities":["image"]},
              "supported_parameters":{"n":{"type":"range","min":1,"max":10}}}]}
            """));
        server.createContext("/api/v1/models", exchange -> {
            if ("output_modalities=rerank".equals(exchange.getRequestURI().getQuery())) {
                respond(exchange,
                    "{\"data\":[{\"id\":\"cohere/rerank\",\"name\":\"Rerank\","
                        + "\"architecture\":{\"output_modalities\":[\"rerank\"]}}]}");
            } else {
                respond(exchange, """
                    {"data":[{"id":"anthropic/chat","name":"Chat",
                      "architecture":{"input_modalities":["text","image","file"]},
                      "supported_parameters":["tools","response_format","reasoning"]}]}
                    """);
            }
        });
        server.start();
        try {
            var baseUrl = baseUrl(server);
            var imageClient = providerType.buildImageGenerationClient(provider(baseUrl), "key",
                "openai/gpt-image-2");
            var result = imageClient.generateImage(GenerateImageRequest.builder()
                .prompt("Halo")
                .n(2)
                .size("2K")
                .aspectRatio("16:9")
                .images(List.of(DataContent.url("https://example.com/ref.png", "image/png")))
                .providerOptions(Map.of("openrouter", Map.of(
                    "quality", "high", "output_format", "webp",
                    "provider", Map.of("only", List.of("openai")))))
                .build()).block();
            assertThat(result.getImage().getBase64()).isEqualTo("YWJj");
            assertThat(result.getImage().getMediaType()).isEqualTo("image/webp");
            assertThat(castMap(result.getUsage().getRaw())).containsEntry("cost", 0.04);
            assertThat(imageBody.get()).containsEntry("quality", "high")
                .containsEntry("output_format", "webp")
                .containsEntry("size", "2K")
                .containsEntry("aspect_ratio", "16:9");

            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "key"))
                .assertNext(models -> {
                    assertThat(models).hasSize(4);
                    assertThat(models).anySatisfy(model -> {
                        assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                        assertThat(model.adapterType()).isEqualTo(AdapterType.OPENROUTER_CHAT);
                        assertThat(model.features()).contains(ModelFeature.VISION,
                            ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT,
                            ModelFeature.REASONING);
                        assertThat(model.capabilities().getLanguage().getFileInput()).isTrue();
                    });
                    assertThat(models).anySatisfy(model -> {
                        assertThat(model.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
                        assertThat(model.capabilities().getImageGeneration()
                            .getMaxImagesPerCall()).isEqualTo(10);
                        assertThat(model.capabilities().getImageGeneration()
                            .getImageToImage()).isTrue();
                    });
                })
                .verifyComplete();
        } finally {
            server.stop(0);
        }
    }

    private Map<String, Object> requestBody(ChatCompletionsOptions options,
        org.springframework.ai.chat.messages.Message... messages) {
        var model = new OpenRouterChatModel(options, WebClient.builder());
        return ReflectionTestUtils.invokeMethod(model, "requestBody",
            new Prompt(List.of(messages)), options, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("openrouter-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openrouter");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        return OBJECT_MAPPER.readValue(exchange.getRequestBody(),
            new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
