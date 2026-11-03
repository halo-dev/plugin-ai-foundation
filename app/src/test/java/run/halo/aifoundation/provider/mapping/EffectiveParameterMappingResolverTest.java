package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.app.extension.Metadata;

class EffectiveParameterMappingResolverTest {

    private final AiProviderType providerType = mock(AiProviderType.class);
    private final EffectiveParameterMappingResolver resolver =
        new EffectiveParameterMappingResolver();

    @BeforeEach
    void setUp() {
        var defaults = Map.of(
            ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-tokens"),
            ModelParameter.REASONING, DefaultParameterMapping.unsupported()
        );
        when(providerType.getSupportedAdapterTypes()).thenReturn(
            java.util.List.of(AdapterType.OPENAI_RESPONSES));
        when(providerType.recommendAdapterType(ModelType.LANGUAGE)).thenReturn(
            java.util.Optional.of(AdapterType.OPENAI_RESPONSES));
        when(providerType.getDefaultParameterMappings(AdapterType.OPENAI_RESPONSES))
            .thenReturn(defaults);
    }

    @Test
    void absentMappingsUseBuiltInDefaults() {
        var effective = resolver.resolve(resolution(null, null));

        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).template())
            .isEqualTo("openai.max-tokens");
        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.BUILT_IN);
        assertThat(effective.get(ModelParameter.REASONING).mode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
    }

    @Test
    void providerOverrideReplacesBuiltIn() {
        var providerMappings = languageMappings(ModelParameter.MAX_OUTPUT_TOKENS,
            template("openai.max-completion-tokens"));

        var effective = resolver.resolve(resolution(providerMappings, null));

        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).template())
            .isEqualTo("openai.max-completion-tokens");
        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.PROVIDER);
    }

    @Test
    void modelOverrideWinsAndOtherFieldsInherit() {
        var providerMappings = languageMappings(ModelParameter.MAX_OUTPUT_TOKENS,
            template("openai.max-completion-tokens"));
        var modelMappings = languageMappings(ModelParameter.REASONING,
            template("reasoning.effort"));

        var effective = resolver.resolve(resolution(providerMappings, modelMappings));

        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.PROVIDER);
        assertThat(effective.get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.effort");
        assertThat(effective.get(ModelParameter.REASONING).source())
            .isEqualTo(EffectiveParameterMappings.Source.MODEL);
    }

    @Test
    void modelCanMarkParameterUnsupported() {
        var modelMappings = languageMappings(ModelParameter.MAX_OUTPUT_TOKENS, unsupported());

        var effective = resolver.resolve(resolution(null, modelMappings));

        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).mode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
        assertThat(effective.get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.MODEL);
    }

    @Test
    void matchingCatalogOverridesDefaultsButExplicitProviderAndModelMappingsWin() {
        var resolution = resolution(null, null);
        var profile = catalogProfile();
        profile.setMappings(languageMappings(ModelParameter.MAX_OUTPUT_TOKENS,
            template("responses.max-output-tokens")));
        resolution.model().getSpec().setCatalogParameters(profile);
        assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.CATALOG);

        resolution.provider().getSpec().setParameterMappings(languageMappings(
            ModelParameter.MAX_OUTPUT_TOKENS, template("openai.max-tokens")));
        assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.PROVIDER);
        resolution.model().getSpec().setParameterMappings(languageMappings(
            ModelParameter.MAX_OUTPUT_TOKENS, unsupported()));
        assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.MODEL);
    }

    @Test
    void mismatchingCatalogIdentityOrAdapterIsIgnoredIncludingConstraints() {
        for (var mismatch : List.of("provider", "model", "adapter")) {
            var resolution = resolution(null, null);
            var profile = catalogProfile();
            profile.setMappings(languageMappings(ModelParameter.MAX_OUTPUT_TOKENS, unsupported()));
            var constraints = new ModelParameterConstraints();
            constraints.setOutputTokens(100L);
            profile.setConstraints(constraints);
            if (mismatch.equals("provider")) {
                profile.setProviderType("other");
            } else if (mismatch.equals("model")) {
                profile.setModelId("other");
            } else {
                profile.setAdapterType(AdapterType.OPENAI_CHAT);
            }
            resolution.model().getSpec().setCatalogParameters(profile);
            assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
                .isEqualTo(EffectiveParameterMappings.Source.BUILT_IN);
            assertThat(resolver.resolveProfile(resolution).getConstraints().getOutputTokens()).isNull();
        }
    }

    @Test
    void explicitConstraintsOverlayCatalogWithoutMutatingSavedFacts() {
        var resolution = resolution(null, null);
        var profile = catalogProfile();
        var automatic = new ModelParameterConstraints();
        automatic.setOutputTokens(100L);
        automatic.setTemperature(false);
        automatic.setToolChoices(List.of("auto"));
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setBudgetMaximum(2000L);
        reasoning.setBudgetMinimum(100L);
        automatic.setReasoning(reasoning);
        profile.setConstraints(automatic);
        resolution.model().getSpec().setCatalogParameters(profile);
        var manual = new ModelParameterConstraints();
        manual.setToolChoices(List.of());
        var manualReasoning = new ModelParameterConstraints.Reasoning();
        manualReasoning.setBudgetMaximum(1000L);
        manual.setReasoning(manualReasoning);
        resolution.model().getSpec().setParameterConstraints(manual);

        var effective = resolver.resolveProfile(resolution);
        assertThat(effective.getConstraints().getToolChoices()).isEmpty();
        assertThat(effective.getConstraints().getOutputTokens()).isEqualTo(100L);
        assertThat(effective.getConstraints().getReasoning().getBudgetMinimum()).isEqualTo(100L);
        assertThat(effective.getConstraints().getReasoning().getBudgetMaximum()).isEqualTo(1000L);
        assertThat(effective.getMappings().getLanguage().getTemperature().getMode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
        assertThat(automatic.getToolChoices()).containsExactly("auto");
        assertThat(automatic.getReasoning().getBudgetMaximum()).isEqualTo(2000L);
    }

    @Test
    void manualMappingsOverrideConflictingCatalogConstraints() {
        var resolution = resolution(languageMappings(ModelParameter.REASONING,
            template("reasoning.responses-effort")), languageMappings(ModelParameter.MAX_OUTPUT_TOKENS,
            template("openai.max-tokens")));
        var profile = catalogProfile();
        var constraints = new ModelParameterConstraints();
        constraints.setOutputTokens(100L);
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setSupported(false);
        constraints.setReasoning(reasoning);
        profile.setConstraints(constraints);
        resolution.model().getSpec().setCatalogParameters(profile);

        var effective = resolver.resolveProfile(resolution);
        assertThat(effective.getConstraints().getOutputTokens()).isNull();
        assertThat(effective.getConstraints().getReasoning()).isNull();
        assertThat(effective.getMappings().getLanguage().getReasoning().getReasoningMapping().getHigh())
            .isNotNull();
    }

    @Test
    void manualReasoningConstraintsRestrictBothRuntimeAndConsoleProfile() {
        var resolution = resolution(null, languageMappings(ModelParameter.REASONING,
            template("reasoning.responses-effort")));
        var constraints = new ModelParameterConstraints();
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setToggle(false);
        reasoning.setEfforts(List.of("high"));
        constraints.setReasoning(reasoning);
        resolution.model().getSpec().setParameterConstraints(constraints);

        var effective = resolver.resolve(resolution).get(ModelParameter.REASONING).reasoningMapping();
        var displayed = resolver.resolveProfile(resolution).getMappings().getLanguage()
            .getReasoning().getReasoningMapping();
        assertThat(effective).isEqualTo(displayed);
        assertThat(effective.getEnabled()).isNull();
        assertThat(effective.getDisabled()).isNull();
        assertThat(effective.getLow()).isNull();
        assertThat(effective.getMedium()).isNull();
        assertThat(effective.getHigh().getValue()).isEqualTo("high");
        assertThat(resolution.model().getSpec().getParameterMappings().getLanguage()
            .getReasoning().getReasoningMapping()).isNull();
    }

    @Test
    void changingProviderEndpointInvalidatesAutomaticFacts() {
        var resolution = resolution(null, null);
        when(providerType.getDefaultBaseUrl()).thenReturn("https://api.openai.com");
        var profile = catalogProfile();
        profile.setProviderBaseUrl("https://api.openai.com");
        profile.setMappings(languageMappings(ModelParameter.MAX_OUTPUT_TOKENS, unsupported()));
        resolution.model().getSpec().setCatalogParameters(profile);
        assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.CATALOG);
        resolution.provider().getSpec().setBaseUrl("https://private.example.com");
        assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.BUILT_IN);
        assertThat(resolver.resolveProfile(resolution).getProviderBaseUrl())
            .isEqualTo("https://private.example.com");
    }

    @Test
    void overridingProviderRouteInvalidatesAutomaticFacts() {
        var resolution = resolution(null, null);
        var profile = catalogProfile();
        profile.setMappings(languageMappings(ModelParameter.MAX_OUTPUT_TOKENS, unsupported()));
        var constraints = new ModelParameterConstraints();
        constraints.setOutputTokens(100L);
        profile.setConstraints(constraints);
        resolution.model().getSpec().setCatalogParameters(profile);
        resolution.provider().getSpec().setChatEndpointPath("/custom/chat");
        assertThat(resolver.resolve(resolution).get(ModelParameter.MAX_OUTPUT_TOKENS).source())
            .isEqualTo(EffectiveParameterMappings.Source.BUILT_IN);
        assertThat(resolver.resolveProfile(resolution).getConstraints().getOutputTokens()).isNull();
    }

    private ModelParameterProfile catalogProfile() {
        var profile = new ModelParameterProfile();
        profile.setModelId("gpt");
        profile.setProviderType("openai");
        profile.setAdapterType(AdapterType.OPENAI_RESPONSES);
        return profile;
    }

    private ModelResolution resolution(ModelParameterMappings providerMappings,
        ModelParameterMappings modelMappings) {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider"));
        var providerSpec = new AiProvider.AiProviderSpec();
        providerSpec.setProviderType("openai");
        providerSpec.setParameterMappings(providerMappings);
        provider.setSpec(providerSpec);
        var model = new AiModel();
        model.setMetadata(metadata("model"));
        var modelSpec = new AiModel.AiModelSpec();
        modelSpec.setProviderName("provider");
        modelSpec.setModelId("gpt");
        modelSpec.setModelType(ModelType.LANGUAGE);
        modelSpec.setParameterMappings(modelMappings);
        model.setSpec(modelSpec);
        return new ModelResolution(model, provider, providerType, "key");
    }

    private ModelParameterMappings languageMappings(ModelParameter parameter,
        ModelParameterMappings.Selection selection) {
        var mappings = new ModelParameterMappings();
        var language = new ModelParameterMappings.LanguageMappings();
        if (parameter == ModelParameter.MAX_OUTPUT_TOKENS) {
            language.setMaxOutputTokens(selection);
        } else if (parameter == ModelParameter.REASONING) {
            language.setReasoning(selection);
        }
        mappings.setLanguage(language);
        return mappings;
    }

    private ModelParameterMappings.Selection template(String id) {
        var selection = new ModelParameterMappings.Selection();
        selection.setMode(ModelParameterMappings.Mode.TEMPLATE);
        selection.setTemplate(id);
        return selection;
    }

    private ModelParameterMappings.Selection unsupported() {
        var selection = new ModelParameterMappings.Selection();
        selection.setMode(ModelParameterMappings.Mode.UNSUPPORTED);
        return selection;
    }

    private Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}