package run.halo.aifoundation.provider;

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
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.deepseek.DeepSeekChatModel;
import run.halo.aifoundation.provider.deepseek.DeepSeekMessagesModel;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekResponsesModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "deepseek",
    officialDocumentation = "https://api-docs.deepseek.com/guides/thinking_mode/; "
        + "https://api-docs.deepseek.com/zh-cn/guides/vision; "
        + "https://api-docs.deepseek.com/api/create-chat-completion/; "
        + "https://api-docs.deepseek.com/zh-cn/guides/responses_api/; "
        + "https://api-docs.deepseek.com/guides/anthropic_api/; "
        + "https://api-docs.deepseek.com/api/list-models/",
    retrievedAt = "2026-08-27"
)
class DeepSeekProviderTest {

    private final DeepSeekProvider providerType = new DeepSeekProvider();

    @Test
    void exposesEveryDocumentedConversationalProtocolWithoutModelNameInspection() {
        var provider = provider("https://api.deepseek.com");

        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(
            AdapterType.DEEPSEEK_CHAT, AdapterType.DEEPSEEK_RESPONSES,
            AdapterType.DEEPSEEK_MESSAGES);
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "opaque-model", ModelType.LANGUAGE, AdapterType.DEEPSEEK_RESPONSES)))
            .isInstanceOf(DeepSeekResponsesModel.class);
        assertThat(providerType.buildChatModel(provider, "key", new ProviderModelRef(
            "opaque-model", ModelType.LANGUAGE, AdapterType.DEEPSEEK_MESSAGES)))
            .isInstanceOf(DeepSeekMessagesModel.class);
        assertThat(providerType.getSupportedFeatures(AdapterType.DEEPSEEK_MESSAGES))
            .contains(run.halo.aifoundation.provider.support.ModelFeature.VISION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagesMapsDocumentedUrlAndFilesApiImageSources() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .build();
        var model = new DeepSeekMessagesModel(options, WebClient.builder());
        var user = UserMessage.builder().text("Describe it").media(List.of(
            new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/a.png")),
            new Media(MimeTypeUtils.IMAGE_PNG, URI.create("file-api-opaque")))).build();
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(user), options, false);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");

        assertThat((Map<String, Object>) content.get(1).get("source"))
            .containsEntry("type", "url")
            .containsEntry("url", "https://example.com/a.png");
        assertThat((Map<String, Object>) content.get(2).get("source"))
            .containsEntry("type", "file")
            .containsEntry("file_id", "file-api-opaque");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesMapsDocumentedImageUrlAndFilesApiSources() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .build();
        var model = new DeepSeekResponsesModel(options, WebClient.builder());
        var media = List.of(
            new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/a.png")),
            new Media(MimeTypeUtils.IMAGE_PNG, URI.create("file-api-opaque")));
        var request = options.mutate().toolContext("deepseek-responses.messages",
            List.of(UserMessage.builder().text("Describe it").media(media).build())).build();
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", request, false);
        var input = (List<Map<String, Object>>) body.get("input");
        var content = (List<Map<String, Object>>) input.getFirst().get("content");

        assertThat(content.get(1))
            .containsEntry("type", "input_image")
            .containsEntry("image_url", "https://example.com/a.png");
        assertThat(content.get(2))
            .containsEntry("type", "input_image")
            .containsEntry("file_id", "file-api-opaque");
        assertThat(providerType.getSupportedFeatures(AdapterType.DEEPSEEK_RESPONSES))
            .contains(run.halo.aifoundation.provider.support.ModelFeature.VISION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesOmitsIgnoredStateFields() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .build();
        var model = new DeepSeekResponsesModel(options, WebClient.builder());

        var stateful = options.mutate().extraBody(Map.of(
            "input", List.of(Map.of("role", "user", "content", "Hello")),
            "previous_response_id", "resp-opaque",
            "store", true,
            "metadata", Map.of("trace", "opaque")))
            .build();
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", stateful, true);

        assertThat(body).doesNotContainKeys(
            "previous_response_id", "store", "metadata", "stream_options");
    }

    @Test
    void messagesUsesPromptOnlyStructuredOutputContract() {
        var adapterOptions = providerType.languageModelProviderOptions(
            AdapterType.DEEPSEEK_MESSAGES);
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.object(Map.of("type", "object")))
            .build();
        var options = (ChatCompletionsOptions) adapterOptions
            .structuredOutputChatOptionsFactory().build(request);

        assertThat(adapterOptions.structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.PROMPT_ONLY);
        assertThat(adapterOptions.nativeStrictToolSchemas()).isFalse();
        assertThat(options.getResponseFormat()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesKeepsCurrentSamplingIdentityAndBuiltInToolFields() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .topLogprobs(7)
            .user("opaque-user")
            .reasoningEffort("max")
            .extraBody(Map.of("tools", List.of(Map.of(
                "type", "web_search",
                "search_context_size", "high"))))
            .build();
        var model = new DeepSeekResponsesModel(options, WebClient.builder());
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);

        assertThat(body).containsEntry("top_logprobs", 7)
            .containsEntry("user", "opaque-user");
        assertThat((Map<String, Object>) body.get("reasoning"))
            .containsEntry("effort", "max");
        assertThat((List<Map<String, Object>>) body.get("tools"))
            .singleElement()
            .satisfies(tool -> assertThat(tool)
                .containsEntry("type", "web_search")
                .containsEntry("search_context_size", "high"));
    }

    @Test
    void responsesUsesJsonSchemaWithoutClaimingStrictFunctionSchemas() {
        var adapterOptions = providerType.languageModelProviderOptions(
            AdapterType.DEEPSEEK_RESPONSES);
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();
        var options = (ChatCompletionsOptions) adapterOptions
            .structuredOutputChatOptionsFactory().build(request);

        assertThat(adapterOptions.structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.JSON_SCHEMA);
        assertThat(adapterOptions.nativeStrictToolSchemas()).isFalse();
        assertThat(options.getResponseFormat().getType())
            .isEqualTo(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesReplaysReasoningAndWebSearchItemsForStatelessTurns() {
        var outputItems = List.of(
            Map.<String, Object>of("type", "reasoning", "content", List.of(
                Map.of("type", "reasoning_text", "text", "Check sources"))),
            Map.<String, Object>of("type", "web_search_call", "id", "search-opaque",
                "action", Map.of("type", "search", "query", "Halo")));
        var assistant = AssistantMessage.builder()
            .content("Found it")
            .properties(Map.of("providerMetadata", Map.of(
                "providerOutputItems", outputItems)))
            .build();
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .toolContext("deepseek-responses.messages", List.of(assistant))
            .build();
        var model = new DeepSeekResponsesModel(options, WebClient.builder());
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, false);

        assertThat((List<Map<String, Object>>) body.get("input"))
            .extracting(item -> item.get("type"))
            .startsWith("reasoning", "web_search_call");
    }

    @Test
    void documentedCatalogIsProviderOwnedAndDoesNotInferPerModelCapabilities() throws Exception {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var body = """
                    {"object":"list","data":[
                      {"id":"opaque-provider-model","object":"model","owned_by":"deepseek"}]}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> assertThat(models).singleElement().satisfies(model -> {
                    assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(model.adapterType()).isEqualTo(AdapterType.DEEPSEEK_CHAT);
                    assertThat(model.features()).containsExactlyInAnyOrderElementsOf(
                        providerType.getSupportedFeatures(AdapterType.DEEPSEEK_CHAT));
                    assertThat(model.source()).isEqualTo(DiscoverySource.RULE);
                    assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.LOW);
                }))
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void structuredOutputOptions_useDeepSeekJsonObjectResponseFormat() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory()
            .build(request);

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType())
            .isEqualTo(ChatCompletionsOptions.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void structuredOutputOptions_useJsonObjectForRawJsonButNotArrayOrChoice() {
        var factory = providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory();

        var jsonOptions = (ChatCompletionsOptions) factory.build(
            GenerateTextRequest.builder().prompt("Generate JSON").output(OutputSpec.json()).build());
        var arrayOptions = (ChatCompletionsOptions) factory.build(
            GenerateTextRequest.builder().prompt("Generate array")
                .output(OutputSpec.array(Map.of("type", "string"))).build());
        var choiceOptions = (ChatCompletionsOptions) factory.build(
            GenerateTextRequest.builder().prompt("Choose")
                .output(OutputSpec.choice(List.of("yes", "no"))).build());

        assertThat(jsonOptions.getResponseFormat().getType())
            .isEqualTo(ChatCompletionsOptions.ResponseFormat.Type.JSON_OBJECT);
        assertThat(arrayOptions.getResponseFormat()).isNull();
        assertThat(choiceOptions.getResponseFormat()).isNull();
        assertThat(providerType.languageModelProviderOptions().structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.JSON_OBJECT);
    }

    @Test
    void strictOpenSchemaDoesNotClaimNativeStrictEnforcement() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.builder()
                .type(run.halo.aifoundation.schema.OutputType.OBJECT)
                .schema(Map.of(
                    "type", "object",
                    "properties", Map.of("name", Map.of("type", "string"))
                ))
                .strict(true)
                .build())
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory().build(request);

        assertThat(options.getResponseFormat().getType())
            .isEqualTo(ChatCompletionsOptions.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void toolOptions_useDeepSeekJsonObjectResponseFormatWithStructuredObjectOutput() {
        var request = GenerateTextRequest.builder()
            .prompt("Use tool then generate JSON")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .inputSchema(Map.of("type", "object"))
                .build()))
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer")
            )))
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType())
            .isEqualTo(ChatCompletionsOptions.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void toolOptions_applyRequiredToolChoice() {
        var request = GenerateTextRequest.builder()
            .prompt("Use a tool")
            .providerOptions(Map.of("deepseek", Map.of(
                "thinking", Map.of("type", "disabled"))))
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .inputSchema(Map.of("type", "object"))
                .build()))
            .toolChoice(ToolChoice.required())
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getToolChoice()).isEqualTo("required");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dedicatedProfileAllowsDocumentedToolChoiceInDefaultThinkingMode() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("deepseek-v4-pro")
            .toolChoice("required")
            .build();
        var model = new DeepSeekChatModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(new UserMessage("Use a tool")), options, false);

        assertThat(body).containsEntry("tool_choice", "required");
    }

    @Test
    void toolOptions_applyNativeStrictToolSchemaWhenRequested() {
        var request = GenerateTextRequest.builder()
            .prompt("Use a strict tool")
            .providerOptions(Map.of("deepseek", Map.of(
                "thinking", Map.of("type", "disabled"))))
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .description("Read Halo test information")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("name", Map.of("type", "string")),
                    "required", List.of("name"),
                    "additionalProperties", false
                ))
                .strict(true)
                .build()))
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getToolStrict()).containsEntry("halo_test_info", true);

        var callback = org.mockito.Mockito.mock(org.springframework.ai.tool.ToolCallback.class);
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(
            org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("halo_test_info")
                .description("Read Halo test information")
                .inputSchema("{\"type\":\"object\",\"properties\":{},"
                    + "\"additionalProperties\":false}")
                .build());
        var betaOptions = options.mutate()
            .baseUrl("https://api.deepseek.com/beta")
            .model("deepseek-v4-pro")
            .toolCallbacks(List.of(callback))
            .build();
        var betaModel = new DeepSeekChatModel(betaOptions, WebClient.builder());
        var betaBody = (Map<?, ?>) ReflectionTestUtils.invokeMethod(betaModel,
            "requestBody", new Prompt(new UserMessage("Use the tool")), betaOptions, false);
        assertThat(betaBody.get("tools")).isNotNull();

        var regularOptions = betaOptions.mutate().baseUrl("https://api.deepseek.com").build();
        var regularModel = new DeepSeekChatModel(regularOptions, WebClient.builder());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(regularModel,
            "requestBody", new Prompt(new UserMessage("Use the tool")), regularOptions, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("strict tools").hasMessageContaining("/beta");
    }

    @Test
    void toolOptions_ignoreInputExamplesWhenProviderHasNoNativeExampleSupport() {
        var request = GenerateTextRequest.builder()
            .prompt("Use a tool with examples")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .description("Read Halo test information")
                .inputSchema(Map.of("type", "object"))
                .inputExamples(List.of(Map.of("name", "example")))
                .build()))
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getToolCallbacks()).isEmpty();
    }

    @Test
    void options_applyTypedDeepSeekLogprobs() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .logprobs(true)
            .topLogprobs(3)
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer")
            )))
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory()
            .build(request);

        assertThat(options.getLogprobs()).isTrue();
        assertThat(options.getTopLogprobs()).isEqualTo(3);
    }

    @Test
    void optionsUseExplicitNativeThinkingSwitch() {
        var request = GenerateTextRequest.builder()
            .prompt("Fast")
            .providerOptions(Map.of("deepseek", Map.of(
                "thinking", Map.of("type", "disabled"))))
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(request);
        assertThat(options.getExtraBody()).containsEntry("thinking",
            Map.of("type", "disabled"));
    }

    @Test
    void optionsUseExplicitNativeThinkingAndEffortValues() {
        var enabledRequest = GenerateTextRequest.builder()
            .prompt("Think")
            .providerOptions(Map.of("deepseek", Map.of(
                "thinking", Map.of("type", "enabled"))))
            .build();

        var enabled = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(enabledRequest);
        var low = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory().build(GenerateTextRequest.builder().prompt("Think")
                .providerOptions(Map.of("deepseek", Map.of("reasoning_effort", "low")))
                .build());

        assertThat(enabled.getExtraBody()).containsEntry("thinking", Map.of("type", "enabled"));
        assertThat(low.getExtraBody()).containsEntry("reasoning_effort", "low");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dedicatedProfile_enforcesThinkingConstraintsAndReplaysReasoningForTools() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .apiKey("test-key")
            .model("deepseek-v4-pro")
            .temperature(0.7)
            .topP(0.9)
            .reasoningEffort("high")
            .extraBody(Map.of("thinking", Map.of("type", "enabled")))
            .build();
        var model = new DeepSeekChatModel(options, WebClient.builder());
        var assistant = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", "Need weather data"))
            .toolCalls(List.of(new AssistantMessage.ToolCall(
                "call_1", "function", "weather", "{}")))
            .build();
        var prompt = new Prompt(List.of(new UserMessage("Weather?"), assistant), options);

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);
        var messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(body).containsEntry("reasoning_effort", "high")
            .doesNotContainKeys("temperature", "top_p", "presence_penalty",
                "frequency_penalty");
        assertThat(messages.get(1)).containsEntry("reasoning_content", "Need weather data");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dedicatedProfile_keepsSamplingControlsWhenThinkingIsDisabled() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .apiKey("test-key")
            .model("deepseek-v4-flash")
            .temperature(0.7)
            .topP(0.9)
            .extraBody(Map.of("thinking", Map.of("type", "disabled")))
            .build();
        var model = new DeepSeekChatModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(new UserMessage("Answer directly")), options, false);

        assertThat(body).containsEntry("temperature", 0.7)
            .containsEntry("top_p", 0.9)
            .containsEntry("thinking", Map.of("type", "disabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dedicatedProfile_mapsDocumentedImageDataAndExternalUrls() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .build();
        var model = new DeepSeekChatModel(options, WebClient.builder());
        var imageData = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1, 2, 3})
            .build();
        var externalImage = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType("image/webp"))
            .data(URI.create("https://example.com/image.webp"))
            .build();
        var message = UserMessage.builder()
            .text("Describe both images")
            .media(imageData, externalImage)
            .build();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(message), options, false);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");

        assertThat(content).extracting(part -> part.get("type"))
            .containsExactly("text", "image_url", "image_url");
        assertThat((Map<String, Object>) content.get(1).get("image_url"))
            .extractingByKey("url").asString().startsWith("data:image/png;base64,");
        assertThat((Map<String, Object>) content.get(2).get("image_url"))
            .containsEntry("url", "https://example.com/image.webp");
    }

    @Test
    void dedicatedProfile_rejectsUndocumentedImageFormatsAndReferences() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .model("opaque-model")
            .build();
        var model = new DeepSeekChatModel(options, WebClient.builder());
        var bitmap = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType("image/bmp"))
            .data(new byte[] {1})
            .build();
        var fileReference = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(URI.create("file:///tmp/image.png"))
            .build();
        var oversizedExternalUrl = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data("https://example.com/" + "a".repeat(8192))
            .build();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(UserMessage.builder().text("Describe")
                .media(bitmap).build()), options, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JPEG, PNG, GIF, or WebP");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(UserMessage.builder().text("Describe")
                .media(fileReference).build()), options, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTP(S) URL");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", new Prompt(UserMessage.builder().text("Describe")
                .media(oversizedExternalUrl).build()), options, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("8192 characters");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dedicatedModel_preservesDeepSeekCacheAndReasoningUsage() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://api.deepseek.com")
            .apiKey("test-key")
            .model("deepseek-v4-pro")
            .build();
        var model = new DeepSeekChatModel(options, WebClient.builder());
        var responseJson = """
            {
              "id": "response-1",
              "model": "deepseek-v4-pro",
              "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": "42"},
                "finish_reason": "stop"
              }],
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 7,
                "total_tokens": 19,
                "prompt_cache_hit_tokens": 10,
                "prompt_cache_miss_tokens": 2,
                "completion_tokens_details": {"reasoning_tokens": 5}
              }
            }
            """;

        var response = (org.springframework.ai.chat.model.ChatResponse)
            ReflectionTestUtils.invokeMethod(model, "chatResponse", responseJson, options);
        var rawUsage = (Map<String, Object>) response.getMetadata().getUsage().getNativeUsage();

        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(12);
        assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(7);
        assertThat(rawUsage).containsEntry("prompt_cache_hit_tokens", 10)
            .containsEntry("prompt_cache_miss_tokens", 2);
        assertThat((Map<String, Object>) rawUsage.get("completion_tokens_details"))
            .containsEntry("reasoning_tokens", 5);
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("deepseek-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("deepseek");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
