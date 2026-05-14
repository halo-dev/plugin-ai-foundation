package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ProviderConsoleEndpointTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private final SecretResolver secretResolver = mock(SecretResolver.class);

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Simulate "openai" as a known provider type
        var openAiType = mock(AiProviderType.class);
        when(openAiType.getProviderType()).thenReturn("openai");
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("openai", openAiType));

        var endpoint = new ProviderConsoleEndpoint(client, providerClientCache, secretResolver);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint())
            .configureClient()
            .build();
    }

    // ---- list ----

    @Test
    void list_returnsAllProviders() {
        when(client.listAll(eq(AiProvider.class), any(), any()))
            .thenReturn(Flux.just(
                provider("openai-prod", "openai"),
                provider("ollama-local", "ollama")
            ));

        webTestClient.get().uri("/providers")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(AiProvider.class)
            .hasSize(2);
    }

    // ---- create ----

    @Test
    void create_validProvider_returns200() {
        var p = provider("new-provider", "openai");
        when(client.create(any(AiProvider.class))).thenReturn(Mono.just(p));

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiProvider.class)
            .consumeWith(response ->
                assertThat(response.getResponseBody().getSpec().getProviderType())
                    .isEqualTo("openai"));
    }

    @Test
    void create_unsupportedProviderType_returns400() {
        var p = provider("bad-provider", "unknown-ai");

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_nullProviderType_returns400() {
        var p = provider("bad-provider", null);

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_nullSpec_returns400() {
        var p = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("no-spec");
        p.setMetadata(metadata);
        // spec is null

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
    }

    // ---- delete ----

    @Test
    void delete_noAssociatedModels_returns204() {
        var p = provider("openai-prod", "openai");
        when(client.listAll(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(p));
        when(client.delete(p)).thenReturn(Mono.just(p));

        webTestClient.delete().uri("/providers/openai-prod")
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    void delete_withAssociatedModels_returns400() {
        var model = model("openai-prod", "gpt-4");
        when(client.listAll(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(model));

        webTestClient.delete().uri("/providers/openai-prod")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void delete_providerNotFound_returns404() {
        when(client.listAll(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.fetch(AiProvider.class, "missing")).thenReturn(Mono.empty());

        webTestClient.delete().uri("/providers/missing")
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

    private AiModel model(String providerName, String modelId) {
        var m = new AiModel();
        var metadata = new Metadata();
        metadata.setName(providerName + "-" + modelId);
        m.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        m.setSpec(spec);
        return m;
    }
}
