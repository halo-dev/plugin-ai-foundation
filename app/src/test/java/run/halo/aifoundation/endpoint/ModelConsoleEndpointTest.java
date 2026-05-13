package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ModelConsoleEndpointTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new ModelConsoleEndpoint(client);
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

    // ---- get ----

    @Test
    void get_existingModel_returns200() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        when(client.fetch(AiModel.class, "gpt-4")).thenReturn(Mono.just(m));

        webTestClient.get().uri("/models/gpt-4")
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiModel.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getMetadata().getName())
                    .isEqualTo("gpt-4"));
    }

    @Test
    void get_notFound_returns404() {
        when(client.fetch(AiModel.class, "missing")).thenReturn(Mono.empty());

        webTestClient.get().uri("/models/missing")
            .exchange()
            .expectStatus().isNotFound();
    }

    // ---- create ----

    @Test
    void create_validModel_returns200() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.create(any(AiModel.class))).thenReturn(Mono.just(m));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiModel.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getMetadata().getName())
                    .isEqualTo("gpt-4"));
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
    void create_duplicateModel_returns409() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(m));

        webTestClient.post().uri("/models")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(m)
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
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
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
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.fetch(AiModel.class, "missing")).thenReturn(Mono.empty());

        webTestClient.put().uri("/models/missing")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(model("missing", "openai-prod", "gpt-4"))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void update_duplicateModel_returns409() {
        var existing = model("gpt-4", "openai-prod", "gpt-4");
        var other = model("gpt-4-turbo", "openai-prod", "gpt-4");
        var updated = model("gpt-4", "openai-prod", "gpt-4");

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider("openai-prod", "openai")));
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(other));
        when(client.fetch(AiModel.class, "gpt-4")).thenReturn(Mono.just(existing));

        webTestClient.put().uri("/models/gpt-4")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updated)
            .exchange()
            .expectStatus().isEqualTo(409);
    }

    // ---- delete ----

    @Test
    void delete_existingModel_returns204() {
        var m = model("gpt-4", "openai-prod", "gpt-4");
        when(client.fetch(AiModel.class, "gpt-4")).thenReturn(Mono.just(m));
        when(client.delete(m)).thenReturn(Mono.just(m));

        webTestClient.delete().uri("/models/gpt-4")
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    void delete_notFound_returns404() {
        when(client.fetch(AiModel.class, "missing")).thenReturn(Mono.empty());

        webTestClient.delete().uri("/models/missing")
            .exchange()
            .expectStatus().isNotFound();
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
        m.setSpec(spec);
        return m;
    }
}
