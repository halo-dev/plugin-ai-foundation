package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ModelConsoleEndpointTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final AiModelService aiModelService = mock(AiModelService.class);
    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private AiProviderType mockType;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        mockType = mock(AiProviderType.class);
        when(mockType.getSupportedModelTypes())
            .thenReturn(List.of(ModelType.LANGUAGE, ModelType.EMBEDDING, ModelType.RERANK,
                ModelType.IMAGE_GENERATION));
        when(mockType.getSupportedFeatures())
            .thenReturn(List.of(ModelFeature.STREAMING, ModelFeature.VISION,
                ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING));
        when(mockType.getSupportedAdapterTypes())
            .thenReturn(List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING,
                AdapterType.COHERE_RERANK, AdapterType.OPENAI_IMAGE));
        when(mockType.recommendAdapterType(ModelType.LANGUAGE))
            .thenReturn(Optional.of(AdapterType.OPENAI_CHAT));
        when(mockType.recommendAdapterType(ModelType.EMBEDDING))
            .thenReturn(Optional.of(AdapterType.OPENAI_EMBEDDING));
        when(mockType.recommendAdapterType(ModelType.RERANK))
            .thenReturn(Optional.of(AdapterType.COHERE_RERANK));
        when(mockType.recommendAdapterType(ModelType.IMAGE_GENERATION))
            .thenReturn(Optional.of(AdapterType.OPENAI_IMAGE));
        when(providerClientCache.getProviderTypeMap()).thenReturn(Map.of("openai", mockType));
        when(providerClientCache.getProviderType("openai")).thenReturn(mockType);

        var modelValidator = new ModelConsoleModelValidator(client, providerClientCache);
        var endpoint = new ModelConsoleEndpoint(client, aiModelService, modelValidator);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint())
            .configureClient()
            .build();
    }

    // ---- list ----

    @Test
    void list_returnsAllModels() {
        when(client.listAll(eq(AiModel.class), any(), any()))
            .thenReturn(Flux.just(
                model("gpt-4", "openai-prod", "gpt-4"),
                model("llama2", "ollama-local", "llama2")
            ));

        webTestClient.get().uri("/models")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(AiModel.class)
            .hasSize(2);
    }

    @Test
    void list_withFieldSelector_returnsFilteredModels() {
        when(client.listAll(eq(AiModel.class), any(), any()))
            .thenReturn(Flux.just(
                model("gpt-4", "openai-prod", "gpt-4")
            ));

        webTestClient.get().uri("/models?fieldSelector=spec.providerName=openai-prod")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(AiModel.class)
            .hasSize(1);
    }

    // ---- create ----

    @Test
    void create_validModel_returns200() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        var generatedName = AiModelNameGenerator.generate("openai-prod", "gpt-4");
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, generatedName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.metadata.name").isEqualTo(generatedName)
            .jsonPath("$.spec.group").doesNotExist();
    }

    @Test
    void create_missingAdapterType_usesProviderTypeRecommendation() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        m.getSpec().setAdapterType(null);
        var generatedName = AiModelNameGenerator.generate("openai-prod", "gpt-4");
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, generatedName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiModel.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getSpec().getAdapterType())
                    .isEqualTo(AdapterType.OPENAI_CHAT));
    }

    @Test
    void create_missingAdapterTypeWithoutRecommendation_returns400() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        m.getSpec().setAdapterType(null);
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(mockType.recommendAdapterType(ModelType.LANGUAGE))
            .thenReturn(Optional.empty());

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_explicitUnsupportedAdapterType_returns400() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        m.getSpec().setAdapterType(AdapterType.OLLAMA_CHAT);
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_supportedEmbeddingModel_returns200() {
        var m = model("embedding", "openai-prod", "text-embedding-3-small");
        m.getSpec().setModelType(ModelType.EMBEDDING);
        m.getSpec().setFeatures(List.of());
        m.getSpec().setAdapterType(AdapterType.OPENAI_EMBEDDING);
        var generatedName = AiModelNameGenerator.generate("openai-prod", "text-embedding-3-small");

        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, generatedName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void create_supportedRerankModel_returns200() {
        var m = model("rerank", "openai-prod", "rerank-v1");
        m.getSpec().setModelType(ModelType.RERANK);
        m.getSpec().setFeatures(List.of());
        m.getSpec().setAdapterType(AdapterType.COHERE_RERANK);
        var generatedName = AiModelNameGenerator.generate("openai-prod", "rerank-v1");

        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, generatedName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void create_supportedImageGenerationModel_returns200() {
        var m = model("image", "openai-prod", "gpt-image-1");
        m.getSpec().setModelType(ModelType.IMAGE_GENERATION);
        m.getSpec().setFeatures(List.of());
        m.getSpec().setAdapterType(AdapterType.OPENAI_IMAGE);
        var generatedName = AiModelNameGenerator.generate("openai-prod", "gpt-image-1");

        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, generatedName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void create_unsupportedModelType_returns400() {
        var m = model("embedding", "openai-prod", "text-embedding-3-small");
        m.getSpec().setModelType(ModelType.EMBEDDING);
        m.getSpec().setAdapterType(AdapterType.OPENAI_EMBEDDING);

        when(mockType.getSupportedModelTypes()).thenReturn(List.of(ModelType.LANGUAGE));
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_unsupportedFeature_returns400() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        m.getSpec().setFeatures(List.of(ModelFeature.TOOL_CALL));

        when(mockType.getSupportedFeatures()).thenReturn(List.of(ModelFeature.STREAMING));
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_modelNameNormalizesIllegalCharactersAndCase() {
        var m = model("ignored", "OpenAI-Prod", "GPT/4.1 Mini");
        var generatedName = AiModelNameGenerator.generate("OpenAI-Prod", "GPT/4.1 Mini");
        when(client.fetch(AiProvider.class, "OpenAI-Prod"))
            .thenReturn(Mono.just(provider("OpenAI-Prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, generatedName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiModel.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getMetadata().getName())
                    .isEqualTo(generatedName));
    }

    @Test
    void create_normalizedNameCollision_usesNextGeneratedName() {
        var firstName = AiModelNameGenerator.generate("openai-prod", "GPT/4");
        var secondName = AiModelNameGenerator.generate("openai-prod", "GPT/4", 1);
        var existing = model(firstName, "openai-prod", "gpt-4");
        var incoming = model("ignored", "openai-prod", "GPT/4");

        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, firstName)).thenReturn(Mono.just(existing));
        when(client.fetch(AiModel.class, secondName)).thenReturn(Mono.empty());
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(incoming)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiModel.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getMetadata().getName())
                    .isEqualTo(secondName));
    }

    @Test
    void create_nullSpec_returns400() {
        var m = new AiModel();
        var metadata = new Metadata();
        metadata.setName("no-spec");
        m.setMetadata(metadata);
        // spec is null

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_nullProviderName_returns400() {
        var m = model("gpt-4", null, "gpt-4");

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_nullModelId_returns400() {
        var m = model("gpt-4", "openai-prod", null);

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_duplicateProviderAndModelId_returns409() {
        var firstName = AiModelNameGenerator.generate("openai-prod", "gpt-4");
        var existing = model(firstName, "openai-prod", "gpt-4");
        var incoming = model("ignored", "openai-prod", "gpt-4");
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(existing));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(incoming)
            .exchange()
            .expectStatus().isEqualTo(409);
    }

    // ---- update ----

    @Test
    void update_existingModel_returns200() {
        var existing = model("gpt-4", "openai-prod", "gpt-4");
        var updated = model("gpt-4", "openai-prod", "gpt-4");
        updated.getSpec().setDisplayName("Updated Name");

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(existing));
        when(client.fetch(AiModel.class, "gpt-4")).thenReturn(Mono.just(existing));
        when(client.update(existing)).thenReturn(Mono.just(existing));

        webTestClient.put().uri("/models/gpt-4")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updated)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiModel.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getSpec().getDisplayName())
                    .isEqualTo("Updated Name"));
    }

    @Test
    void update_notFound_returns404() {
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, "missing")).thenReturn(Mono.empty());

        webTestClient.put().uri("/models/missing")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(model("missing", "openai-prod", "gpt-4"))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void update_sameProviderAndModelId_returns200() {
        var existing = model("gpt-4", "openai-prod", "gpt-4");
        var updated = model("gpt-4", "openai-prod", "gpt-4");

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(existing));
        when(client.fetch(AiModel.class, "gpt-4")).thenReturn(Mono.just(existing));
        when(client.update(existing)).thenReturn(Mono.just(existing));

        webTestClient.put().uri("/models/gpt-4")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updated)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void update_duplicateProviderAndModelId_returns409() {
        var existing = model("gpt-4", "openai-prod", "gpt-4");
        var other = model("gpt-4-turbo", "openai-prod", "gpt-4");
        var updated = model("gpt-4", "openai-prod", "gpt-4");

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(other));
        when(client.fetch(AiModel.class, "gpt-4")).thenReturn(Mono.just(existing));

        webTestClient.put().uri("/models/gpt-4")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updated)
            .exchange()
            .expectStatus().isEqualTo(409);
    }

    @Test
    void testUiMessageChatStream_usesUiMessageChatRequestAndResponseHeader() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.start("assistant-1"),
                TextStreamPart.textStart("text-1"),
                TextStreamPart.textDelta("text-1", "Hi"),
                TextStreamPart.textEnd("text-1"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post().uri("/models/gpt-4/test-chat/ui-message/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(Map.of(
                    "id", "user-1",
                    "role", "user",
                    "parts", List.of(Map.of(
                        "type", "text",
                        "id", "user-text",
                        "text", "Hello"
                    )),
                    "metadata", Map.of("conversationId", "chat-1")
                )),
                "trigger", "submit-message",
                "system", "You are concise.",
                "temperature", 0.2,
                "maxOutputTokens", 128
            ))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectHeader().valueEquals("X-Halo-AI-UI-Message-Stream", "v1")
            .expectHeader().doesNotExist("X-Halo-AI-Stream-Protocol")
            .expectBody(String.class)
            .consumeWith(response -> {
                var bodyText = response.getResponseBody();
                assertThat(bodyText).contains("\"type\":\"start\"");
                assertThat(bodyText).contains("\"type\":\"text-delta\"");
                assertThat(bodyText).contains("\"delta\":\"Hi\"");
                assertThat(bodyText).contains("[DONE]");
                assertThat(bodyText).doesNotContain("data: data:");
            });

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getSystem()).isEqualTo("You are concise.");
        assertThat(request.getTemperature()).isEqualTo(0.2);
        assertThat(request.getMaxOutputTokens()).isEqualTo(128);
        assertThat(request.getCancellationToken()).isNotNull();
        assertThat(request.getMessages()).hasSize(1);
        assertThat(request.getMessages().getFirst().getRole().name()).isEqualTo("USER");
        assertThat(request.getMessages().getFirst().getContent().getFirst().getText())
            .isEqualTo("Hello");
    }

    @Test
    void testUiMessageChatStream_withConsoleTestTool_reusesToolInjection() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.toolCall(run.halo.aifoundation.tool.ToolCall.builder()
                    .toolCallId("call_1")
                    .toolName("halo_test_info")
                    .input(Map.of("query", "hello"))
                    .build()),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post()
            .uri("/models/gpt-4/test-chat/ui-message/stream?enableTestTool=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(uiMessageBody("请调用测试工具"))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .consumeWith(response -> {
                var bodyText = response.getResponseBody();
                assertThat(bodyText).contains("\"type\":\"tool-halo_test_info\"");
                assertThat(bodyText).contains("\"state\":\"input-available\"");
                assertThat(bodyText).contains("halo_test_info");
            });

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getStopWhen()).isNotNull();
        assertThat(request.getTools())
            .singleElement()
            .satisfies(tool -> {
                assertThat(tool.getName()).isEqualTo("halo_test_info");
                assertThat(tool.getExecutor()).isNotNull();
            });
    }

    @Test
    void testUiMessageChatStream_regenerateTruncatesTargetAssistantMessage() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("text-2", "New answer"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post().uri("/models/gpt-4/test-chat/ui-message/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "trigger", "regenerate-message",
                "messageId", "assistant-1",
                "messages", List.of(
                    uiMessage("user-1", "user", "user-text", "Question"),
                    uiMessage("assistant-1", "assistant", "old-text", "Old answer"),
                    uiMessage("user-2", "user", "later-text", "Later question")
                )
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages()).hasSize(1);
        assertThat(request.getMessages().getFirst().getRole().name()).isEqualTo("USER");
        assertThat(request.getMessages().getFirst().getContent().getFirst().getText())
            .isEqualTo("Question");
    }

    @Test
    void testUiMessageChatStream_regenerateWithoutMessageIdReturns400WithoutCallingModel() {
        webTestClient.post().uri("/models/gpt-4/test-chat/ui-message/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "trigger", "regenerate-message",
                "messages", List.of(uiMessage("user-1", "user", "user-text", "Question"))
            ))
            .exchange()
            .expectStatus().isBadRequest();

        verify(aiModelService, never()).languageModel(any());
    }

    @Test
    void testUiMessageChatStream_dropsReasoningHistoryForConsoleParityWithTextMode() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("text-1", "Continued"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post().uri("/models/gpt-4/test-chat/ui-message/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(
                    uiMessage("user-1", "user", "user-text", "Question"),
                    uiMessageWithParts("assistant-1", List.of(
                        Map.of(
                            "type", "reasoning",
                            "id", "reasoning-1",
                            "text", "private reasoning"
                        ),
                        Map.of(
                            "type", "text",
                            "id", "answer-1",
                            "text", "Answer"
                        )
                    )),
                    uiMessage("user-2", "user", "user-text-2", "Continue")
                ),
                "trigger", "submit-message"
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages()).hasSize(3);
        assertThat(request.getMessages().get(1).getContent())
            .extracting(run.halo.aifoundation.message.ModelMessagePart::getType)
            .containsExactly("text");
    }

    @Test
    void testUiMessageChatStream_preservesReasoningHistoryWhenProviderSupportsIt() {
        var languageModel = mock(LanguageModel.class);
        when(languageModel.capabilities())
            .thenReturn(LanguageModelCapabilities.supportsReasoningHistory());
        when(aiModelService.languageModel("deepseek-chat")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("text-1", "Continued"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post().uri("/models/deepseek-chat/test-chat/ui-message/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(
                    uiMessage("user-1", "user", "user-text", "Question"),
                    uiMessageWithParts("assistant-1", List.of(
                        Map.of(
                            "type", "reasoning",
                            "id", "reasoning-1",
                            "text", "private reasoning",
                            "providerMetadata", Map.of("deepseek", Map.of("id", "reasoning-id"))
                        ),
                        Map.of(
                            "type", "text",
                            "id", "answer-1",
                            "text", "Answer"
                        )
                    )),
                    uiMessage("user-2", "user", "user-text-2", "Continue")
                ),
                "trigger", "submit-message"
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages()).hasSize(3);
        assertThat(request.getMessages().get(1).getContent())
            .extracting(run.halo.aifoundation.message.ModelMessagePart::getType)
            .containsExactly("reasoning", "text");
    }

    @Test
    void testUiMessageChatStream_acceptsApprovalResponseContinuation() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("text-1", "Approved"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post()
            .uri("/models/gpt-4/test-chat/ui-message/stream?enableTestToolApproval=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(
                    uiMessage("user-1", "user", "user-text", "请调用测试工具"),
                    uiMessageWithParts("assistant-1", List.of(
                        Map.of(
                            "type", "tool-halo_test_info",
                            "toolCallId", "call_1",
                            "toolName", "halo_test_info",
                            "state", "approval-responded",
                            "input", Map.of("query", "hello"),
                            "approval", Map.of(
                                "id", "approval_1",
                                "approved", true,
                                "reason", "console approved"
                            )
                        )
                    ))
                ),
                "trigger", "submit-message"
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages()).hasSize(3);
        assertThat(request.getMessages().get(1).getContent())
            .extracting(run.halo.aifoundation.message.ModelMessagePart::getType)
            .containsExactly("tool-call", "tool-approval-request");
        assertThat(request.getMessages().get(2).getRole().name()).isEqualTo("TOOL");
        assertThat(request.getMessages().get(2).getContent().getFirst().getType())
            .isEqualTo("tool-approval-response");
    }

    @Test
    void testUiMessageChatStream_acceptsExternalToolContinuation() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("text-1", "Continued"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post()
            .uri("/models/gpt-4/test-chat/ui-message/stream?enableExternalTestTool=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(
                    uiMessage("user-1", "user", "user-text", "请调用外部测试工具"),
                    uiMessageWithParts("assistant-1", List.of(
                        Map.of(
                            "type", "tool-halo_external_test_info",
                            "toolCallId", "call_1",
                            "toolName", "halo_external_test_info",
                            "state", "output-available",
                            "input", Map.of("query", "hello"),
                            "output", Map.of("ok", true)
                        )
                    ))
                ),
                "trigger", "submit-message"
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages()).hasSize(3);
        assertThat(request.getMessages().get(1).getContent().getFirst().getType())
            .isEqualTo("tool-call");
        assertThat(request.getMessages().get(2).getRole().name()).isEqualTo("TOOL");
        assertThat(request.getMessages().get(2).getContent().getFirst().getType())
            .isEqualTo("tool-result");
    }

    @Test
    void testUiMessageChatStream_acceptsExternalToolErrorContinuation() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("text-1", "Tool failed"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            )));

        webTestClient.post()
            .uri("/models/gpt-4/test-chat/ui-message/stream?enableExternalTestTool=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(
                    uiMessage("user-1", "user", "user-text", "请调用外部测试工具"),
                    uiMessageWithParts("assistant-1", List.of(
                        Map.of(
                            "type", "tool-halo_external_test_info",
                            "toolCallId", "call_1",
                            "toolName", "halo_external_test_info",
                            "state", "output-error",
                            "input", Map.of("query", "hello"),
                            "errorText", "external failed"
                        )
                    ))
                ),
                "trigger", "submit-message"
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages()).hasSize(3);
        assertThat(request.getMessages().get(2).getRole().name()).isEqualTo("TOOL");
        assertThat(request.getMessages().get(2).getContent().getFirst().getType())
            .isEqualTo("tool-error");
    }

    @Test
    void testUiMessageChatStream_rejectsUnknownPartTypeWithoutCallingModel() {
        webTestClient.post().uri("/models/gpt-4/test-chat/ui-message/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "id", "chat-1",
                "messages", List.of(Map.of(
                    "id", "user-1",
                    "role", "user",
                    "parts", List.of(Map.of("type", "unknown"))
                ))
            ))
            .exchange()
            .expectStatus().isBadRequest();

        verify(aiModelService, never()).languageModel(any());
    }

    @Test
    void testCompletionStream_mapsPromptAndOptionsToGenerateTextRequest() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(streamResult(Flux.just(
                TextStreamPart.textDelta("txt_1", "Hello"),
                TextStreamPart.textDelta("txt_1", " world")
            )));

        webTestClient.post().uri("/models/gpt-4/test-completion/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "prompt", "Say hello",
                "temperature", 0.2,
                "maxOutputTokens", 64
            ))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody(String.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .isEqualTo("Hello world"));

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getPrompt()).isEqualTo("Say hello");
        assertThat(request.getMessages()).isNull();
        assertThat(request.getTemperature()).isEqualTo(0.2);
        assertThat(request.getMaxOutputTokens()).isEqualTo(64);
    }

    @Test
    void testObjectStream_prefersExplicitOutputAndStreamsJsonText() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(structuredStreamResult(Flux.just(
                TextStreamPart.textDelta("txt_1", "{\"title\":\"Halo\"}")
            )));

        var schema = Map.of(
            "type", "object",
            "required", List.of("title"),
            "properties", Map.of("title", Map.of("type", "string"))
        );
        webTestClient.post().uri("/models/gpt-4/test-object/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "input", "Summarize Halo",
                "schema", schema,
                "output", Map.of(
                    "type", "object",
                    "schema", schema
                )
            ))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody(String.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .isEqualTo("{\"title\":\"Halo\"}"));

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        var request = captor.getValue();
        assertThat(request.getPrompt()).isEqualTo("Summarize Halo");
        assertThat(request.getOutput()).isNotNull();
        assertThat(request.getOutput().getType().name()).isEqualTo("OBJECT");
        assertThat(request.getOutput().getSchema()).isEqualTo(schema);
    }

    @Test
    void testObjectStream_derivesOutputFromSchema() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamText(any(GenerateTextRequest.class)))
            .thenReturn(structuredStreamResult(Flux.just(
                TextStreamPart.textDelta("txt_1", "{\"ok\":true}")
            )));

        webTestClient.post().uri("/models/gpt-4/test-object/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "input", "Return ok",
                "schema", Map.of(
                    "type", "object",
                    "properties", Map.of("ok", Map.of("type", "boolean"))
                )
            ))
            .exchange()
            .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(captor.capture());
        assertThat(captor.getValue().getOutput().getType().name()).isEqualTo("OBJECT");
    }

    @Test
    void testObjectStream_rejectsUnsupportedOutputTypeWithoutCallingModel() {
        webTestClient.post().uri("/models/gpt-4/test-object/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "input", "Return ok",
                "output", Map.of("type", "array")
            ))
            .exchange()
            .expectStatus().isBadRequest();

        verify(aiModelService, never()).languageModel(any());
    }

    // ---- helpers ----

    private StreamTextResult streamResult(Flux<TextStreamPart> fullStream) {
        var shared = fullStream.cache();
        return new StreamTextResult(
            shared,
            shared.filter(part -> PartType.TEXT_DELTA.equals(part.getType()))
                .map(TextStreamPart::getDelta),
            Flux.empty(),
            Flux.empty(),
            Mono.empty(),
            Mono.empty()
        );
    }

    private StreamTextResult structuredStreamResult(Flux<TextStreamPart> fullStream) {
        var shared = fullStream.cache();
        return new StreamTextResult(
            shared,
            shared.filter(part -> PartType.TEXT_DELTA.equals(part.getType()))
                .map(TextStreamPart::getDelta),
            Flux.empty(),
            Flux.empty(),
            Mono.just(Map.of("ok", true)),
            Mono.just(run.halo.aifoundation.chat.GenerateTextResult.builder()
                .output(Map.of("ok", true))
                .build())
        );
    }

    private Map<String, Object> uiMessageBody(String text) {
        return Map.of(
            "id", "chat-1",
            "messages", List.of(uiMessage("user-1", "user", "user-text", text)),
            "trigger", "submit-message"
        );
    }

    private Map<String, Object> uiMessage(String id, String role, String partId, String text) {
        return Map.of(
            "id", id,
            "role", role,
            "parts", List.of(Map.of(
                "type", "text",
                "id", partId,
                "text", text
            )),
            "metadata", Map.of("conversationId", "chat-1")
        );
    }

    private Map<String, Object> uiMessageWithParts(String id, List<Map<String, Object>> parts) {
        return Map.of(
            "id", id,
            "role", "assistant",
            "parts", parts,
            "metadata", Map.of("conversationId", "chat-1")
        );
    }

    private AiProvider provider(String name, String providerType) {
        var p = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        p.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setDisplayName(name);
        spec.setEnabled(true);
        p.setSpec(spec);
        return p;
    }

    private AiModel model(String name, String providerName, String modelId) {
        var m = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        m.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        spec.setDisplayName(name);
        spec.setModelType(ModelType.LANGUAGE);
        spec.setFeatures(List.of(ModelFeature.STREAMING));
        spec.setAdapterType(AdapterType.OPENAI_CHAT);
        m.setSpec(spec);
        return m;
    }
}
