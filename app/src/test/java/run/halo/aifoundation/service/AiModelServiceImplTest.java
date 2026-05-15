package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.ModelNotFoundException;
import run.halo.aifoundation.ProviderDisabledException;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class AiModelServiceImplTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderClientCache providerClientCache;

    @Mock
    SecretResolver secretResolver;

    AiModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiModelServiceImpl(client, providerClientCache, secretResolver);
    }

    // ---- listModels ----

    @Test
    void listModels_returnsAllModels() {
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                aiModel("openai-prod-gpt-4-abc", "provider-a", "gpt-4", "GPT-4", true),
                aiModel("ollama-local-claude-3-xyz", "provider-b", "claude-3", "Claude 3", true)
            ));

        StepVerifier.create(service.listModels())
            .assertNext(models -> {
                assertThat(models).hasSize(2);
                assertThat(models.get(0).getName()).isEqualTo("openai-prod-gpt-4-abc");
                assertThat(models.get(0).getModelId()).isEqualTo("gpt-4");
                assertThat(models.get(0).getProviderName()).isEqualTo("provider-a");
                assertThat(models.get(1).getName()).isEqualTo("ollama-local-claude-3-xyz");
                assertThat(models.get(1).getModelId()).isEqualTo("claude-3");
            })
            .verifyComplete();
    }

    @Test
    void listModels_emptyResult_returnsEmptyList() {
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        StepVerifier.create(service.listModels())
            .assertNext(models -> assertThat(models).isEmpty())
            .verifyComplete();
    }

    // ---- listProviders ----

    @Test
    void listProviders_returnsAllProviders() {
        var provider1 = aiProvider("openai-prod", "openai", true);
        provider1.setStatus(statusWithPhase(AiProvider.AiProviderStatus.Phase.OK));
        var provider2 = aiProvider("ollama-local", "ollama", false);

        when(client.listAll(eq(AiProvider.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(provider1, provider2));

        StepVerifier.create(service.listProviders())
            .assertNext(providers -> {
                assertThat(providers).hasSize(2);
                assertThat(providers.get(0).getName()).isEqualTo("openai-prod");
                assertThat(providers.get(0).getProviderType()).isEqualTo("openai");
                assertThat(providers.get(0).isEnabled()).isTrue();
                assertThat(providers.get(0).getPhase()).isEqualTo("OK");
                assertThat(providers.get(1).isEnabled()).isFalse();
                assertThat(providers.get(1).getPhase()).isEqualTo("UNKNOWN");
            })
            .verifyComplete();
    }

    @Test
    void listProviders_nullStatus_showsUnknownPhase() {
        var provider = aiProvider("my-provider", "openai", true);
        provider.setStatus(null);
        when(client.listAll(eq(AiProvider.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(provider));

        StepVerifier.create(service.listProviders())
            .assertNext(providers -> assertThat(providers.get(0).getPhase()).isEqualTo("UNKNOWN"))
            .verifyComplete();
    }

    // ---- languageModel — fetch by metadata.name ----

    @Test
    void languageModel_modelNotFound_emitsModelNotFoundException() {
        when(client.fetch(AiModel.class, "nonexistent-model")).thenReturn(Mono.empty());

        StepVerifier.create(service.languageModel("nonexistent-model"))
            .expectError(ModelNotFoundException.class)
            .verify();
    }

    @Test
    void languageModel_disabledModel_emitsModelDisabledException() {
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", false);
        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));

        StepVerifier.create(service.languageModel("openai-prod-gpt-4-abc"))
            .expectErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(run.halo.aifoundation.ModelDisabledException.class)
                .hasMessageContaining("openai-prod-gpt-4-abc"))
            .verify();
    }

    @Test
    void languageModel_disabledProvider_emitsProviderDisabledException() {
        var provider = aiProvider("openai-prod", "openai", false);
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", true);

        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));

        StepVerifier.create(service.languageModel("openai-prod-gpt-4-abc"))
            .expectError(ProviderDisabledException.class)
            .verify();
    }

    @Test
    void languageModel_providerNotFound_emitsModelNotFoundException() {
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", true);
        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.empty());

        StepVerifier.create(service.languageModel("openai-prod-gpt-4-abc"))
            .expectErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("Provider not found"))
            .verify();
    }

    // ---- helpers ----

    private AiModel aiModel(String name, String providerName, String modelId,
                            String displayName, boolean enabled) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        spec.setDisplayName(displayName);
        spec.setEnabled(enabled);
        model.setSpec(spec);
        return model;
    }

    private AiProvider aiProvider(String name, String providerType, boolean enabled) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setDisplayName(name);
        spec.setEnabled(enabled);
        provider.setSpec(spec);
        return provider;
    }

    private AiProvider.AiProviderStatus statusWithPhase(AiProvider.AiProviderStatus.Phase phase) {
        var status = new AiProvider.AiProviderStatus();
        status.setPhase(phase);
        return status;
    }
}
