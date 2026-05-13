package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class ProviderConsoleEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    ProviderConsoleEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ProviderConsoleEndpoint(client);
    }

    // ---- list ----

    @Test
    void list_returnsAllProviders() {
        when(client.list(eq(AiProvider.class), isNull(), isNull()))
            .thenReturn(Flux.just(
                provider("openai-prod", "openai"),
                provider("ollama-local", "ollama")
            ));

        StepVerifier.create(endpoint.list())
            .assertNext(result -> assertThat(result.getItems()).hasSize(2))
            .verifyComplete();
    }

    // ---- get ----

    @Test
    void get_existingProvider_returnsIt() {
        var p = provider("openai-prod", "openai");
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(p));

        StepVerifier.create(endpoint.get("openai-prod"))
            .assertNext(result -> assertThat(result.getMetadata().getName()).isEqualTo("openai-prod"))
            .verifyComplete();
    }

    @Test
    void get_notFound_returns404() {
        when(client.fetch(AiProvider.class, "missing")).thenReturn(Mono.empty());

        StepVerifier.create(endpoint.get("missing"))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            })
            .verify();
    }

    // ---- create ----

    @Test
    void create_validProvider_createsAndReturns() {
        var p = provider("new-provider", "openai");
        when(client.create(p)).thenReturn(Mono.just(p));

        StepVerifier.create(endpoint.create(p))
            .assertNext(result ->
                assertThat(result.getSpec().getProviderType()).isEqualTo("openai"))
            .verifyComplete();
    }

    @Test
    void create_unsupportedProviderType_returns400() {
        var p = provider("bad-provider", "unknown-ai");

        StepVerifier.create(endpoint.create(p))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            })
            .verify();
    }

    @Test
    void create_nullProviderType_returns400() {
        var p = provider("bad-provider", null);

        StepVerifier.create(endpoint.create(p))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            })
            .verify();
    }

    @Test
    void create_nullSpec_returns400() {
        var p = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("no-spec");
        p.setMetadata(metadata);
        // spec is null

        StepVerifier.create(endpoint.create(p))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            })
            .verify();
    }

    // ---- update ----

    @Test
    void update_existingProvider_updatesSpec() {
        var existing = provider("openai-prod", "openai");
        var updated = provider("openai-prod", "openai");
        updated.getSpec().setDisplayName("Updated Name");

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(existing));
        when(client.update(existing)).thenReturn(Mono.just(existing));

        StepVerifier.create(endpoint.update("openai-prod", updated))
            .assertNext(result ->
                assertThat(result.getSpec().getDisplayName()).isEqualTo("Updated Name"))
            .verifyComplete();
    }

    @Test
    void update_notFound_returns404() {
        when(client.fetch(AiProvider.class, "missing")).thenReturn(Mono.empty());

        StepVerifier.create(endpoint.update("missing", provider("missing", "openai")))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            })
            .verify();
    }

    // ---- delete ----

    @Test
    void delete_noAssociatedModels_deletesSuccessfully() {
        var p = provider("openai-prod", "openai");
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(p));
        when(client.delete(p)).thenReturn(Mono.just(p));

        StepVerifier.create(endpoint.delete("openai-prod"))
            .assertNext(response ->
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
            .verifyComplete();
    }

    @Test
    void delete_withAssociatedModels_returns400() {
        var model = model("openai-prod", "gpt-4");
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(model));

        StepVerifier.create(endpoint.delete("openai-prod"))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(err.getMessage()).contains("associated AI models");
            })
            .verify();
    }

    @Test
    void delete_providerNotFound_returns404() {
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());
        when(client.fetch(AiProvider.class, "missing")).thenReturn(Mono.empty());

        StepVerifier.create(endpoint.delete("missing"))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) err).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            })
            .verify();
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
