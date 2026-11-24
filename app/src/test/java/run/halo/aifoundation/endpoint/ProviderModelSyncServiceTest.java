package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ProviderModelSyncServiceTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ModelConsoleModelValidator validator = mock(ModelConsoleModelValidator.class);
    private final ProviderModelSyncService service = new ProviderModelSyncService(client, validator);

    @BeforeEach
    void setUp() {
        when(validator.validate(any(AiModel.class))).thenReturn(Mono.empty());
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(client.update(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void createPersistsValidatedModelWithGeneratedName() {
        var model = savedModel("openai-prod", "gpt-4");
        model.getMetadata().setName("");
        when(client.fetch(eq(AiModel.class), anyString())).thenReturn(Mono.empty());

        var response = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(
                                List.of(new ModelSyncOperation(ModelSyncAction.CREATE, "gpt-4", null, model, null))))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.CREATED);
        assertThat(response.results().get(0).modelName()).isNotBlank();
        verify(validator).validate(model);
    }

    @Test
    void updateAppliesRemoteProfileAndPreservesAdministratorFields() {
        var saved = savedModel("openai-prod", "gpt-4");
        saved.getMetadata().setName("saved-gpt-4");
        saved.getSpec().setDisplayName("My GPT");
        saved.getSpec().setEnabled(false);
        saved.getSpec().setNativeOptions(Map.of("temperature", 0.2));
        var mappings = new ModelParameterMappings();
        saved.getSpec().setParameterMappings(mappings);
        var constraints = new ModelParameterConstraints();
        saved.getSpec().setParameterConstraints(constraints);
        when(client.fetch(AiModel.class, "saved-gpt-4")).thenReturn(Mono.just(saved));

        var remote = discovered("gpt-4", ModelType.EMBEDDING, AdapterType.OPENAI_EMBEDDING, List.of());

        var response = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.UPDATE, "gpt-4", "saved-gpt-4", null, remote))))
                .block();

        assertThat(response.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.UPDATED);
        var updated = response.results().get(0);
        assertThat(updated.modelName()).isEqualTo("saved-gpt-4");
        assertThat(saved.getSpec().getDisplayName()).isEqualTo("My GPT");
        assertThat(saved.getSpec().isEnabled()).isFalse();
        assertThat(saved.getSpec().getNativeOptions()).containsEntry("temperature", 0.2);
        assertThat(saved.getSpec().getParameterMappings()).isSameAs(mappings);
        assertThat(saved.getSpec().getParameterConstraints()).isSameAs(constraints);
        assertThat(saved.getSpec().getModelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(saved.getSpec().getAdapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
        assertThat(saved.getSpec().getFeatures()).isEmpty();
        assertThat(saved.getSpec().getDiscoverySource()).isEqualTo(DiscoverySource.REMOTE);
        verify(client).update(saved);
    }

    @Test
    void updateKeepsManualCapabilityDomain() {
        var saved = savedModel("openai-prod", "gpt-4");
        saved.getMetadata().setName("saved-gpt-4");
        saved.getSpec()
                .setCapabilities(ModelCapabilities.builder()
                        .language(LanguageCapability.builder().imageInput(false).build())
                        .sources(ModelCapabilitySources.builder()
                                .language(CapabilitySource.MANUAL)
                                .imageGeneration(CapabilitySource.REMOTE)
                                .build())
                        .build());
        saved.getSpec()
                .setCapabilitySources(ModelCapabilitySources.builder()
                        .language(CapabilitySource.MANUAL)
                        .imageGeneration(CapabilitySource.REMOTE)
                        .build());
        when(client.fetch(AiModel.class, "saved-gpt-4")).thenReturn(Mono.just(saved));

        var remote = discovered(
                "gpt-4",
                ModelType.LANGUAGE,
                AdapterType.OPENAI_CHAT,
                List.of(ModelFeature.STREAMING, ModelFeature.TOOL_CALL),
                ModelCapabilities.builder()
                        .language(LanguageCapability.builder().imageInput(true).build())
                        .build(),
                ModelCapabilitySources.builder()
                        .language(CapabilitySource.REMOTE)
                        .build());

        service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.UPDATE, "gpt-4", "saved-gpt-4", null, remote))))
                .block();

        assertThat(saved.getSpec().getCapabilities().getLanguage().getImageInput())
                .isFalse();
        assertThat(saved.getSpec().getCapabilitySources().getLanguage()).isEqualTo(CapabilitySource.MANUAL);
        assertThat(saved.getSpec().getFeatures()).containsExactly(ModelFeature.STREAMING);
    }

    @Test
    void updateSkipsManuallyCreatedModel() {
        var saved = savedModel("openai-prod", "gpt-4");
        saved.getMetadata().setName("saved-gpt-4");
        saved.getSpec().setDiscoverySource(DiscoverySource.MANUAL);
        when(client.fetch(AiModel.class, "saved-gpt-4")).thenReturn(Mono.just(saved));

        var response = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(new ModelSyncOperation(
                                ModelSyncAction.UPDATE,
                                "gpt-4",
                                "saved-gpt-4",
                                null,
                                discovered("gpt-4", ModelType.EMBEDDING, AdapterType.OPENAI_EMBEDDING, List.of())))))
                .block();

        assertThat(response.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.SKIPPED);
        verify(client, never()).update(any(AiModel.class));
        assertThat(saved.getSpec().getModelType()).isEqualTo(ModelType.LANGUAGE);
    }

    @Test
    void updateWithoutRemoteProfileIsSkipped() {
        var response = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.UPDATE, "gpt-4", "saved-gpt-4", null, null))))
                .block();

        assertThat(response.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.SKIPPED);
        verifyNoInteractions(client);
    }

    @Test
    void enableAndDisableChangeOnlyEnabledState() {
        var saved = savedModel("openai-prod", "gpt-4");
        saved.getMetadata().setName("saved-gpt-4");
        saved.getSpec().setDisplayName("My GPT");
        when(client.fetch(AiModel.class, "saved-gpt-4")).thenReturn(Mono.just(saved));

        var disabled = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.DISABLE, "gpt-4", "saved-gpt-4", null, null))))
                .block();
        assertThat(disabled.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.DISABLED);
        assertThat(saved.getSpec().isEnabled()).isFalse();
        assertThat(saved.getSpec().getDisplayName()).isEqualTo("My GPT");

        var enabled = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.ENABLE, "gpt-4", "saved-gpt-4", null, null))))
                .block();
        assertThat(enabled.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.ENABLED);
        assertThat(saved.getSpec().isEnabled()).isTrue();
    }

    @Test
    void ignoreDoesNotTouchSavedModel() {
        var response = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.IGNORE, "gpt-4", "saved-gpt-4", null, null))))
                .block();

        assertThat(response.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.SKIPPED);
        verifyNoInteractions(client);
    }

    @Test
    void oneFailedOperationDoesNotStopTheBatch() {
        var saved = savedModel("openai-prod", "gpt-4");
        saved.getMetadata().setName("saved-gpt-4");
        when(client.fetch(AiModel.class, "missing"))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")));
        when(client.fetch(AiModel.class, "saved-gpt-4")).thenReturn(Mono.just(saved));

        var response = service.sync(
                        "openai-prod",
                        new ModelSyncRequest(List.of(
                                new ModelSyncOperation(ModelSyncAction.ENABLE, "gpt-4", "missing", null, null),
                                new ModelSyncOperation(ModelSyncAction.ENABLE, "gpt-4", "saved-gpt-4", null, null))))
                .block();

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).outcome()).isEqualTo(ModelSyncOutcome.FAILED);
        assertThat(response.results().get(0).message()).isEqualTo("Model not found");
        assertThat(response.results().get(1).outcome()).isEqualTo(ModelSyncOutcome.ENABLED);
    }

    private static AiModel savedModel(String providerName, String modelId) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(providerName + "-" + modelId);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        spec.setDisplayName(modelId);
        spec.setEnabled(true);
        spec.setModelType(ModelType.LANGUAGE);
        spec.setAdapterType(AdapterType.OPENAI_CHAT);
        spec.setFeatures(List.of(ModelFeature.STREAMING));
        spec.setDiscoverySource(DiscoverySource.RULE);
        spec.setDiscoveryConfidence(DiscoveryConfidence.LOW);
        model.setSpec(spec);
        return model;
    }

    private static DiscoveredModelItem discovered(
            String modelId, ModelType modelType, AdapterType adapterType, List<ModelFeature> features) {
        return discovered(modelId, modelType, adapterType, features, null, null);
    }

    private static DiscoveredModelItem discovered(
            String modelId,
            ModelType modelType,
            AdapterType adapterType,
            List<ModelFeature> features,
            ModelCapabilities capabilities,
            ModelCapabilitySources capabilitySources) {
        return new DiscoveredModelItem(
                modelId,
                modelId,
                "",
                modelType,
                features,
                DiscoverySource.REMOTE,
                DiscoveryConfidence.HIGH,
                adapterType,
                capabilities,
                capabilitySources,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                List.of());
    }
}
