package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.catalog.ModelCatalogEnricher;
import run.halo.aifoundation.catalog.ModelCatalogMatch;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.mapping.ParameterMappingValidator;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ModelParameterConsoleEndpointTest {
    private WebTestClient web;
    private final ModelCatalogEnricher catalog = mock(ModelCatalogEnricher.class);
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private AiProvider provider;

    @BeforeEach
    void setup() {
        var cache = mock(ProviderClientCache.class);
        provider = new AiProvider();
        var providerSpec = new AiProvider.AiProviderSpec();
        providerSpec.setProviderType("openai");
        provider.setSpec(providerSpec);
        var type = new OpenAiProvider();
        when(client.fetch(AiProvider.class, "provider")).thenReturn(Mono.just(provider));
        when(cache.getProviderTypeMap()).thenReturn(Map.of("openai", type));
        when(cache.getProviderType("openai")).thenReturn(type);
        var validator = new ModelConsoleModelValidator(client, cache,
            new ParameterMappingValidator(new ParameterMappingTemplateRegistry()));
        var endpoint = new ModelParameterConsoleEndpoint(client, cache,
            new EffectiveParameterMappingResolver(), validator, catalog);
        web = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void catalogPreviewUsesSelectedAdapterWithoutProviderDiscovery() {
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().adapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
            assertThat(models.getFirst().modelId()).isEqualTo("test-model");
            return List.of(models.getFirst().toBuilder()
                .catalogParameters(profile("test-model")).build());
        });
        web.post().uri("/model-parameters/catalog").bodyValue(model()).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.adapterType").isEqualTo("openai-chat");
    }

    @Test
    void missingCatalogMatchReturnsNoContent() {
        when(catalog.enrich(any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));
        web.post().uri("/model-parameters/catalog").bodyValue(model()).exchange()
            .expectStatus().isNoContent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai-embedding", "openai-image"})
    void nonLanguageCatalogPreviewPreservesSelectedTypeAndResolvesCapturedFacts(String adapterId) {
        var adapter = AdapterType.fromValue(adapterId);
        var draft = model();
        draft.getSpec().setModelType(adapter.getModelType());
        draft.getSpec().setAdapterType(adapter);
        var captured = profile("test-model");
        captured.setAdapterType(adapter);
        captured.setFeatureEvidence(Map.of());
        var constraints = new ModelParameterConstraints();
        constraints.setInputTokens(8191L);
        captured.setConstraints(constraints);
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().modelType()).isEqualTo(adapter.getModelType());
            assertThat(models.getFirst().confidence()).isEqualTo(DiscoveryConfidence.HIGH);
            return List.of(models.getFirst().toBuilder().catalogParameters(captured).build());
        });
        var result = web.post().uri("/model-parameters/catalog").bodyValue(draft).exchange()
            .expectStatus().isOk().expectBody(ModelParameterProfile.class).returnResult()
            .getResponseBody();
        assertThat(result).isEqualTo(captured);
        draft.getSpec().setCatalogParameters(result);
        web.post().uri("/model-parameters/resolve").bodyValue(draft).exchange()
            .expectStatus().isOk().expectBody()
            .jsonPath("$.constraints.inputTokens").isEqualTo(8191)
            .jsonPath("$.constraints.outputTokens").isEmpty();
    }

    @Test
    void refreshesCatalogFieldsAndMappingsUsingRecordedProviderEvidence() {
        var saved = savedModel();
        var captured = profile("test-model");
        captured.setConstraints(featureConstraints(true, false, true));
        captured.getConstraints().setOutputTokens(1024L);
        saved.getSpec().setCatalogParameters(captured);
        saved.getSpec().setDiscoverySource(DiscoverySource.REMOTE);
        var evidence = Map.of(ModelFeature.TOOL_CALL, true, ModelFeature.STRUCTURED_OUTPUT, false,
            ModelFeature.REASONING, true);
        captured.setFeatureEvidence(evidence);
        var updated = profile("test-model");
        updated.setFeatureEvidence(evidence);
        updated.setConstraints(featureConstraints(true, false, true));
        updated.getConstraints().setOutputTokens(8192L);
        updated.getConstraints().setTemperature(false);
        var mappings = new ModelParameterMappings();
        var language = new ModelParameterMappings.LanguageMappings();
        var maxOutput = new ModelParameterMappings.Selection();
        maxOutput.setMode(ModelParameterMappings.Mode.TEMPLATE);
        maxOutput.setTemplate("openai.max-completion-tokens");
        maxOutput.setField("max_completion_tokens");
        language.setMaxOutputTokens(maxOutput);
        mappings.setLanguage(language);
        updated.setMappings(mappings);
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().featureEvidence()).isEqualTo(evidence);
            assertThat(models.getFirst().features()).isEmpty();
            return List.of(models.getFirst().toBuilder().catalogParameters(updated).build());
        });

        var result = preview(saved);
        assertThat(result).isEqualTo(updated);
        assertThat(saved.getSpec().getCatalogParameters()).isSameAs(captured);
    }

    @ParameterizedTest
    @EnumSource(value = DiscoverySource.class, names = {"REMOTE", "RULE"})
    void catalogAttributedFeaturesAreNotPromotedToProviderEvidence(DiscoverySource source) {
        var saved = savedModel();
        var captured = profile("test-model");
        captured.setConstraints(featureConstraints(true, true, true));
        saved.getSpec().setCatalogParameters(captured);
        saved.getSpec().setDiscoverySource(source);
        saved.getSpec().setFeatures(List.of(ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT,
            ModelFeature.REASONING));
        saved.getSpec().setCatalogMatch(provenance(List.of("parameters", "features.tool-call",
            "features.structured-output", "features.reasoning")));
        var updated = profile("test-model");
        updated.setConstraints(featureConstraints(false, false, false));
        updated.setFeatureEvidence(Map.of());
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().featureEvidence()).isEmpty();
            assertThat(models.getFirst().features()).isEmpty();
            return List.of(models.getFirst().toBuilder().catalogParameters(updated).build());
        });

        assertThat(preview(saved)).isEqualTo(updated);
    }

    @Test
    void recordedProviderEvidenceDoesNotDependOnCatalogAttribution() {
        var saved = savedModel();
        var captured = profile("test-model");
        captured.setConstraints(featureConstraints(false, true, false));
        var evidence = Map.of(ModelFeature.TOOL_CALL, true, ModelFeature.STRUCTURED_OUTPUT, false);
        captured.setFeatureEvidence(evidence);
        saved.getSpec().setCatalogParameters(captured);
        saved.getSpec().setDiscoverySource(DiscoverySource.REMOTE);
        saved.getSpec().setCatalogMatch(provenance(List.of("parameters", "features.tool-call")));
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().featureEvidence()).isEqualTo(evidence);
            return models;
        });

        assertThat(preview(saved)).isEqualTo(captured);
    }

    @Test
    void newlyKnownCatalogBooleansRemainRefreshableAfterSavingAndReopening() {
        var saved = savedModel();
        saved.getSpec().setCatalogParameters(profile("test-model"));
        saved.getSpec().setDiscoverySource(DiscoverySource.REMOTE);
        saved.getSpec().setCatalogMatch(provenance(List.of("parameters")));
        var newlyKnown = profile("test-model");
        newlyKnown.setConstraints(featureConstraints(true, true, true));
        newlyKnown.setFeatureEvidence(Map.of());
        var changedLater = profile("test-model");
        changedLater.setConstraints(featureConstraints(false, false, false));
        changedLater.setFeatureEvidence(Map.of());
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().featureEvidence()).isEmpty();
            var updated = saved.getSpec().getCatalogParameters().getConstraints() == null
                ? newlyKnown : changedLater;
            return List.of(models.getFirst().toBuilder().catalogParameters(updated).build());
        });

        var firstPreview = preview(saved);
        assertThat(firstPreview).isEqualTo(newlyKnown);
        saved.getSpec().setCatalogParameters(firstPreview);
        assertThat(preview(saved)).isEqualTo(changedLater);
    }

    @Test
    void unavailableCatalogKeepsTheApplicableSnapshotWithoutModifyingTheSavedModel() {
        var saved = savedModel();
        var captured = profile("test-model");
        captured.setFeatureEvidence(Map.of(ModelFeature.TOOL_CALL, false));
        saved.getSpec().setCatalogParameters(captured);
        when(catalog.enrich(any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));

        assertThat(preview(saved)).isEqualTo(captured);
        assertThat(saved.getSpec().getCatalogParameters()).isSameAs(captured);
    }

    private ModelParameterProfile preview(AiModel saved) {
        var draft = model();
        draft.setMetadata(saved.getMetadata());
        return web.post().uri("/model-parameters/catalog").bodyValue(draft).exchange()
            .expectStatus().isOk().expectBody(ModelParameterProfile.class).returnResult()
            .getResponseBody();
    }

    private ModelCatalogMatch provenance(List<String> fields) {
        return new ModelCatalogMatch("openai", "test-model", Instant.EPOCH, fields);
    }

    private ModelParameterConstraints featureConstraints(boolean tools, boolean structured,
        boolean reasoningSupported) {
        var constraints = new ModelParameterConstraints();
        constraints.setToolCalling(tools);
        constraints.setStructuredOutput(structured);
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setSupported(reasoningSupported);
        constraints.setReasoning(reasoning);
        return constraints;
    }

    @Test
    void changedAdapterRetrievesANewCatalogProfile() {
        var saved = savedModel();
        saved.getSpec().setCatalogParameters(profile("test-model"));
        var draft = model();
        draft.setMetadata(saved.getMetadata());
        draft.getSpec().setAdapterType(AdapterType.OPENAI_RESPONSES);
        var updated = profile("test-model");
        updated.setAdapterType(AdapterType.OPENAI_RESPONSES);
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().adapterType()).isEqualTo(AdapterType.OPENAI_RESPONSES);
            return List.of(models.getFirst().toBuilder().catalogParameters(updated).build());
        });
        web.post().uri("/model-parameters/catalog").bodyValue(draft).exchange()
            .expectStatus().isOk().expectBody()
            .jsonPath("$.adapterType").isEqualTo("openai-responses");
    }

    @ParameterizedTest
    @ValueSource(strings = {"modelId", "providerName", "baseUrl", "chatEndpointPath",
        "profileProviderType", "profileBaseUrl"})
    void changedIdentityCannotReviveSavedCatalogParameters(String field) {
        var saved = savedModel();
        saved.getSpec().setCatalogParameters(profile("test-model"));
        saved.getSpec().getCatalogParameters()
            .setFeatureEvidence(Map.of(ModelFeature.TOOL_CALL, true));
        var draft = model();
        draft.setMetadata(saved.getMetadata());
        switch (field) {
            case "modelId" -> draft.getSpec().setModelId("another-model");
            case "providerName" -> {
                draft.getSpec().setProviderName("another-provider");
                when(client.fetch(AiProvider.class, "another-provider"))
                    .thenReturn(Mono.just(provider));
            }
            case "baseUrl" -> provider.getSpec().setBaseUrl("https://another.example/v1");
            case "chatEndpointPath" -> provider.getSpec().setChatEndpointPath("/custom/chat");
            case "profileProviderType" -> saved.getSpec().getCatalogParameters()
                .setProviderType("deepseek");
            case "profileBaseUrl" -> saved.getSpec().getCatalogParameters()
                .setProviderBaseUrl(null);
            default -> throw new IllegalArgumentException(field);
        }
        when(catalog.enrich(any(), any(), any())).thenAnswer(invocation -> {
            List<DiscoveredModel> models = invocation.getArgument(2);
            assertThat(models.getFirst().featureEvidence()).isEmpty();
            assertThat(models.getFirst().features()).isEmpty();
            return models;
        });
        web.post().uri("/model-parameters/catalog").bodyValue(draft).exchange()
            .expectStatus().isNoContent();
    }

    private AiModel savedModel() {
        var saved = model();
        var metadata = new Metadata();
        metadata.setName("saved-model");
        saved.setMetadata(metadata);
        when(client.fetch(AiModel.class, "saved-model")).thenReturn(Mono.just(saved));
        return saved;
    }

    @Test
    void resolvesDraftWithoutCredentialsAndExpandsTemplateIntents() {
        var result = web.post().uri("/model-parameters/resolve").bodyValue(model()).exchange()
            .expectStatus().isOk().expectBody(ModelParameterProfile.class).returnResult()
            .getResponseBody();
        assertThat(result).isNotNull();
        assertThat(result.getMappings().getLanguage().getReasoning().getReasoningMapping()
            .getLow().getValue()).isEqualTo("low");
        assertThat(result.getModelId()).isEqualTo("test-model");
        assertThat(result.getAdapterType()).isEqualTo(AdapterType.OPENAI_CHAT);
    }

    @Test
    void rejectsInvalidAutomaticConstraintsBeforeUse() {
        var model = model();
        var profile = profile("test-model");
        var constraints = new ModelParameterConstraints();
        constraints.setOutputTokens(-1L);
        profile.setConstraints(constraints);
        model.getSpec().setCatalogParameters(profile);
        web.post().uri("/model-parameters/resolve").bodyValue(model).exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void staleAutomaticProfileDoesNotAffectNewModelIdentity() {
        var model = model();
        var profile = profile("previous-model");
        var constraints = new ModelParameterConstraints();
        constraints.setTemperature(false);
        profile.setConstraints(constraints);
        model.getSpec().setCatalogParameters(profile);
        var result = web.post().uri("/model-parameters/resolve").bodyValue(model).exchange()
            .expectStatus().isOk().expectBody(ModelParameterProfile.class).returnResult()
            .getResponseBody();
        assertThat(result.getMappings().getLanguage().getTemperature().getMode())
            .isEqualTo(ModelParameterMappings.Mode.TEMPLATE);
        assertThat(result.getConstraints().getTemperature()).isNull();
    }

    private ModelParameterProfile profile(String modelId) {
        var result = new ModelParameterProfile();
        result.setProviderType("openai");
        result.setProviderBaseUrl("https://api.openai.com/v1");
        result.setModelId(modelId);
        result.setAdapterType(AdapterType.OPENAI_CHAT);
        return result;
    }

    private AiModel model() {
        var result = new AiModel();
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("provider");
        spec.setModelId("test-model");
        spec.setAdapterType(AdapterType.OPENAI_CHAT);
        spec.setModelType(ModelType.LANGUAGE);
        result.setSpec(spec);
        return result;
    }
}