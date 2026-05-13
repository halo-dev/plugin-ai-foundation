package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.ModelNotFoundException;
import run.halo.aifoundation.ProviderDisabledException;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.ProviderClientCache;
import run.halo.aifoundation.provider.SecretResolver;
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
        when(client.list(eq(AiModel.class), isNull(), isNull()))
            .thenReturn(Flux.just(
                aiModel("provider-a", "gpt-4", "GPT-4"),
                aiModel("provider-b", "claude-3", "Claude 3")
            ));

        StepVerifier.create(service.listModels())
            .assertNext(models -> {
                assertThat(models).hasSize(2);
                assertThat(models.get(0).getModelId()).isEqualTo("gpt-4");
                assertThat(models.get(0).getProviderName()).isEqualTo("provider-a");
                assertThat(models.get(1).getModelId()).isEqualTo("claude-3");
            })
            .verifyComplete();
    }

    @Test
    void listModels_emptyResult_returnsEmptyList() {
        when(client.list(eq(AiModel.class), isNull(), isNull())).thenReturn(Flux.empty());

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

        when(client.list(eq(AiProvider.class), isNull(), isNull()))
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
        when(client.list(eq(AiProvider.class), isNull(), isNull()))
            .thenReturn(Flux.just(provider));

        StepVerifier.create(service.listProviders())
            .assertNext(providers -> assertThat(providers.get(0).getPhase()).isEqualTo("UNKNOWN"))
            .verifyComplete();
    }

    // ---- languageModel — parseModelRef validation ----

    @Test
    void languageModel_nullModelRef_throwsModelNotFoundException() {
        assertThatThrownBy(() -> service.languageModel(null))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void languageModel_modelRefWithoutSlash_throwsModelNotFoundException() {
        assertThatThrownBy(() -> service.languageModel("no-slash-here"))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void languageModel_modelRefWithBlankProviderName_throwsModelNotFoundException() {
        assertThatThrownBy(() -> service.languageModel("/gpt-4"))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void languageModel_modelRefWithBlankModelId_throwsModelNotFoundException() {
        assertThatThrownBy(() -> service.languageModel("openai/"))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void languageModel_disabledProvider_throwsProviderDisabledException() {
        var provider = aiProvider("openai-prod", "openai", false);
        var model = aiModel("openai-prod", "gpt-4", "GPT-4");

        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));

        assertThatThrownBy(() -> service.languageModel("openai-prod/gpt-4"))
            .isInstanceOf(ProviderDisabledException.class);
    }

    @Test
    void languageModel_providerNotFound_throwsModelNotFoundException() {
        var model = aiModel("openai-prod", "gpt-4", "GPT-4");
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.languageModel("openai-prod/gpt-4"))
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("Provider not found");
    }

    @Test
    void languageModel_modelNotFound_throwsModelNotFoundException() {
        when(client.list(eq(AiModel.class), any(), isNull())).thenReturn(Flux.empty());

        assertThatThrownBy(() -> service.languageModel("openai-prod/gpt-4"))
            .isInstanceOf(ModelNotFoundException.class);
    }

    // ---- helpers ----

    private AiModel aiModel(String providerName, String modelId, String displayName) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(providerName + "-" + modelId);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        spec.setDisplayName(displayName);
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
