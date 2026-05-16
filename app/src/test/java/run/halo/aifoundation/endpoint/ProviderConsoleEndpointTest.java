package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelCapability;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ProviderConsoleEndpointTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private final SecretResolver secretResolver = mock(SecretResolver.class);
    private AiProviderType openAiType;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Simulate "openai" as a known provider type
        openAiType = mock(AiProviderType.class);
        when(openAiType.getProviderType()).thenReturn("openai");
        when(openAiType.requiresBaseUrl()).thenReturn(false);
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("openai", openAiType));
        when(providerClientCache.getProviderType("openai")).thenReturn(openAiType);

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
    void create_validProxyConfig_returns200() {
        var p = provider("new-provider", "openai");
        p.getSpec().setProxyHost("127.0.0.1");
        p.getSpec().setProxyPort(7890);
        when(client.create(any(AiProvider.class))).thenReturn(Mono.just(p));

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AiProvider.class)
            .consumeWith(response -> {
                var spec = response.getResponseBody().getSpec();
                assertThat(spec.getProxyHost()).isEqualTo("127.0.0.1");
                assertThat(spec.getProxyPort()).isEqualTo(7890);
            });
    }

    @Test
    void create_proxyHostWithoutProxyPort_returns400() {
        var p = provider("bad-provider", "openai");
        p.getSpec().setProxyHost("127.0.0.1");

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_proxyPortWithoutProxyHost_returns400() {
        var p = provider("bad-provider", "openai");
        p.getSpec().setProxyPort(7890);

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_blankProxyHostWithProxyPort_returns400() {
        var p = provider("bad-provider", "openai");
        p.getSpec().setProxyHost(" ");
        p.getSpec().setProxyPort(7890);

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void create_invalidProxyPort_returns400() {
        var p = provider("bad-provider", "openai");
        p.getSpec().setProxyHost("127.0.0.1");
        p.getSpec().setProxyPort(0);

        webTestClient.post().uri("/providers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(p)
            .exchange()
            .expectStatus().isBadRequest();
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
    void delete_withAssociatedModels_returns204() {
        var p = provider("openai-prod", "openai");
        var model = model("openai-prod", "gpt-4");
        when(client.listAll(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(p));
        when(client.delete(model)).thenReturn(Mono.just(model));
        when(client.delete(p)).thenReturn(Mono.just(p));

        webTestClient.delete().uri("/providers/openai-prod")
            .exchange()
            .expectStatus().isNoContent();

        verify(client).delete(model);
        verify(client).delete(p);
    }

    @Test
    void delete_providerNotFound_returns404() {
        when(client.listAll(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.fetch(AiProvider.class, "missing")).thenReturn(Mono.empty());

        webTestClient.delete().uri("/providers/missing")
            .exchange()
            .expectStatus().isNotFound();
    }

    // ---- discover models ----

    @Test
    void discoverModels_returnsSuggestedEndpointTypes() {
        var provider = provider("openai-prod", "openai");
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));
        when(secretResolver.resolveApiKey(isNull())).thenReturn(Mono.just("sk-test"));
        when(openAiType.discoverModels(provider, "sk-test")).thenReturn(Mono.just(List.of(
            new DiscoveredModel("gpt-4", "gpt-4", Set.of(ModelCapability.CHAT)),
            new DiscoveredModel("text-embedding-3-small", "text-embedding-3-small",
                Set.of(ModelCapability.EMBEDDING))
        )));
        when(openAiType.recommendEndpointType(any(DiscoveredModel.class))).thenAnswer(invocation -> {
            DiscoveredModel model = invocation.getArgument(0);
            return model.capabilities().contains(ModelCapability.EMBEDDING)
                ? Optional.of("openai-embedding") : Optional.of("openai-chat");
        });

        webTestClient.get().uri("/providers/openai-prod/discover-models")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.providerName").isEqualTo("openai-prod")
            .jsonPath("$.models[0].modelId").isEqualTo("gpt-4")
            .jsonPath("$.models[0].suggestedEndpointType").isEqualTo("openai-chat")
            .jsonPath("$.models[1].modelId").isEqualTo("text-embedding-3-small")
            .jsonPath("$.models[1].suggestedEndpointType").isEqualTo("openai-embedding");
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
