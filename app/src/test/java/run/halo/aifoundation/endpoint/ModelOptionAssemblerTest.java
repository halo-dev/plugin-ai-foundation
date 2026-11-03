package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.app.extension.Metadata;

class ModelOptionAssemblerTest {

    private final ModelOptionAssembler assembler = new ModelOptionAssembler(
        new ModelCapabilityService(), new ModelCapabilityMatcher(),
        new EffectiveParameterMappingResolver());
    private final AiProviderType providerType = mock(AiProviderType.class);
    private AiProvider provider;
    private AiModel model;
    private ModelParameterConstraints catalogConstraints;

    @BeforeEach
    void setUp() {
        when(providerType.getSupportedAdapterTypes()).thenReturn(List.of(AdapterType.OPENAI_RESPONSES));
        when(providerType.recommendAdapterType(ModelType.LANGUAGE))
            .thenReturn(Optional.of(AdapterType.OPENAI_RESPONSES));
        when(providerType.getDefaultParameterMappings(AdapterType.OPENAI_RESPONSES))
            .thenReturn(Map.of());
        when(providerType.getDefaultBaseUrl()).thenReturn("https://api.openai.com/v1");
        provider = new AiProvider();
        provider.setMetadata(metadata("provider"));
        var providerSpec = new AiProvider.AiProviderSpec();
        providerSpec.setProviderType("openai");
        providerSpec.setEnabled(true);
        provider.setSpec(providerSpec);
        model = new AiModel();
        model.setMetadata(metadata("model"));
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("provider");
        spec.setModelId("model-id");
        spec.setModelType(ModelType.LANGUAGE);
        spec.setEnabled(true);
        model.setSpec(spec);
        var profile = new ModelParameterProfile();
        profile.setModelId("model-id");
        profile.setProviderType("openai");
        profile.setProviderBaseUrl("https://api.openai.com/v1");
        profile.setAdapterType(AdapterType.OPENAI_RESPONSES);
        catalogConstraints = new ModelParameterConstraints();
        profile.setConstraints(catalogConstraints);
        spec.setCatalogParameters(profile);
    }

    @Test
    void excludesStaleFeaturesFromOptionsAndRequiredFeatureFilters() {
        model.getSpec().setFeatures(List.of(ModelFeature.STREAMING, ModelFeature.TOOL_CALL,
            ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING));
        catalogConstraints.setToolCalling(false);
        catalogConstraints.setStructuredOutput(false);
        catalogConstraints.setReasoning(reasoning(false));

        assertThat(options(List.of()).getFirst().getFeatures())
            .containsExactly(ModelFeature.STREAMING);
        for (var feature : List.of(ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT,
            ModelFeature.REASONING)) {
            assertThat(options(List.of(feature))).isEmpty();
        }
        assertThat(model.getSpec().getFeatures()).hasSize(4);
    }

    @Test
    void addsSupportedFeaturesEvenWhenReasoningHasNoPortableControl() {
        model.getSpec().setFeatures(List.of(ModelFeature.STREAMING));
        catalogConstraints.setToolCalling(true);
        catalogConstraints.setStructuredOutput(true);
        catalogConstraints.setReasoning(reasoning(true));
        var mappings = new ModelParameterMappings();
        var language = new ModelParameterMappings.LanguageMappings();
        var unsupported = new ModelParameterMappings.Selection();
        unsupported.setMode(ModelParameterMappings.Mode.UNSUPPORTED);
        language.setReasoning(unsupported);
        mappings.setLanguage(language);
        model.getSpec().getCatalogParameters().setMappings(mappings);

        assertThat(options(List.of(ModelFeature.REASONING)).getFirst().getFeatures())
            .containsExactly(ModelFeature.STREAMING, ModelFeature.TOOL_CALL,
                ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING);
    }

    @Test
    void explicitManualFeatureConstraintsWinInBothDirections() {
        model.getSpec().setFeatures(List.of(ModelFeature.STRUCTURED_OUTPUT));
        catalogConstraints.setToolCalling(false);
        catalogConstraints.setStructuredOutput(true);
        catalogConstraints.setReasoning(reasoning(false));
        var manual = new ModelParameterConstraints();
        manual.setToolCalling(true);
        manual.setStructuredOutput(false);
        manual.setReasoning(reasoning(true));
        model.getSpec().setParameterConstraints(manual);

        assertThat(options(List.of(ModelFeature.TOOL_CALL)).getFirst().getFeatures())
            .containsExactly(ModelFeature.TOOL_CALL, ModelFeature.REASONING);
        assertThat(options(List.of(ModelFeature.STRUCTURED_OUTPUT))).isEmpty();
    }

    @Test
    void manualReasoningMappingKeepsFeatureSelectionIndependentOfCatalogControl() {
        model.getSpec().setFeatures(List.of(ModelFeature.REASONING));
        catalogConstraints.setReasoning(reasoning(false));
        var mappings = new ModelParameterMappings();
        var language = new ModelParameterMappings.LanguageMappings();
        var selection = new ModelParameterMappings.Selection();
        selection.setMode(ModelParameterMappings.Mode.TEMPLATE);
        selection.setTemplate("reasoning.responses-effort");
        language.setReasoning(selection);
        mappings.setLanguage(language);
        for (var modelOverride : List.of(false, true)) {
            provider.getSpec().setParameterMappings(modelOverride ? null : mappings);
            model.getSpec().setParameterMappings(modelOverride ? mappings : null);
            assertThat(options(List.of(ModelFeature.REASONING)).getFirst().getFeatures())
                .containsExactly(ModelFeature.REASONING);
        }
    }

    @Test
    void ignoresCatalogSnapshotAfterEndpointChanges() {
        model.getSpec().setFeatures(List.of(ModelFeature.TOOL_CALL));
        catalogConstraints.setToolCalling(false);
        provider.getSpec().setBaseUrl("https://gateway.example/v1");

        assertThat(options(List.of(ModelFeature.TOOL_CALL)).getFirst().getFeatures())
            .containsExactly(ModelFeature.TOOL_CALL);
    }

    @Test
    void unknownCatalogFieldsAndMissingProvidersPreserveLegacyFeatures() {
        model.getSpec().setFeatures(List.of(ModelFeature.TOOL_CALL, ModelFeature.REASONING));
        assertThat(options(List.of()).getFirst().getFeatures())
            .containsExactly(ModelFeature.TOOL_CALL, ModelFeature.REASONING);
        catalogConstraints.setToolCalling(false);
        assertThat(assembler.toOptions(List.of(model), Map.of(), Map.of(), query(List.of()))
            .getFirst().getFeatures()).containsExactly(ModelFeature.TOOL_CALL, ModelFeature.REASONING);
    }

    private List<ModelOption> options(List<ModelFeature> required) {
        return assembler.toOptions(List.of(model), Map.of("provider", provider),
            Map.of("openai", providerType), query(required));
    }

    private ModelOptionQuery query(List<ModelFeature> required) {
        return new ModelOptionQuery(null, null, null, null, null, required, null, null);
    }

    private ModelParameterConstraints.Reasoning reasoning(boolean supported) {
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setSupported(supported);
        return reasoning;
    }

    private Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}