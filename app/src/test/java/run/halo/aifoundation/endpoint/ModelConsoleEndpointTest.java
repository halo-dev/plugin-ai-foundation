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
import run.halo.aifoundation.ChatChunk;
import run.halo.aifoundation.ChatRequest;
import run.halo.aifoundation.ChunkType;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.Message;
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

        var endpoint = new ModelConsoleEndpoint(client, aiModelService, providerClientCache);
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

    // ---- test chat stream ----

    @Test
    void testChatStream_mapsMessagesAndOptionsToChatRequest() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamChat(any(ChatRequest.class)))
            .thenReturn(Flux.just(
                ChatChunk.builder().type(ChunkType.TEXT).content("Hi").build(),
                ChatChunk.builder().type(ChunkType.FINISH).last(true).finishReason("stop").build()
            ));

        var body = Map.of(
            "messages", List.of(
                Map.of("role", "system", "content", "You are concise."),
                Map.of("role", "user", "content", "Hello")
            ),
            "temperature", 0.2,
            "maxTokens", 128,
            "topP", 0.9,
            "providerOptions", Map.of("seed", 42)
        );

        webTestClient.post().uri("/models/gpt-4/test-chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBodyList(ChatChunk.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ChatChunk::getType)
                .containsExactly(ChunkType.TEXT, ChunkType.FINISH));

        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(languageModel).streamChat(captor.capture());
        var request = captor.getValue();
        assertThat(request.getMessages())
            .extracting(Message::getRole)
            .containsExactly("system", "user");
        assertThat(request.getMessages())
            .extracting(Message::getContent)
            .containsExactly("You are concise.", "Hello");
        assertThat(request.getTemperature()).isEqualTo(0.2);
        assertThat(request.getMaxTokens()).isEqualTo(128);
        assertThat(request.getTopP()).isEqualTo(0.9);
        assertThat(request.getProviderOptions()).containsEntry("seed", 42);
    }

    @Test
    void testChatStream_emptyMessages_returns400WithoutCallingModelService() {
        webTestClient.post().uri("/models/gpt-4/test-chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("messages", List.of()))
            .exchange()
            .expectStatus().isBadRequest();

        verify(aiModelService, never()).languageModel(any());
    }

    @Test
    void testChatStream_streamErrorEmitsErrorChunk() {
        var languageModel = mock(LanguageModel.class);
        when(aiModelService.languageModel("gpt-4")).thenReturn(Mono.just(languageModel));
        when(languageModel.streamChat(any(ChatRequest.class)))
            .thenReturn(Flux.error(new IllegalStateException("upstream failed")));

        webTestClient.post().uri("/models/gpt-4/test-chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("messages", List.of(Map.of("role", "user", "content", "Hello"))))
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ChatChunk.class)
            .consumeWith(response -> {
                assertThat(response.getResponseBody()).hasSize(1);
                var chunk = response.getResponseBody().getFirst();
                assertThat(chunk.getType()).isEqualTo(ChunkType.ERROR);
                assertThat(chunk.getContent()).contains("upstream failed");
            });
    }

    // ---- helpers ----

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
