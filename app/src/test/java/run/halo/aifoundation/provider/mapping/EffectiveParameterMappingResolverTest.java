package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.app.extension.Metadata;

class EffectiveParameterMappingResolverTest {

    private final AiProviderType providerType = mock(AiProviderType.class);
    private final EffectiveParameterMappingResolver resolver =
        new EffectiveParameterMappingResolver();

    @BeforeEach
    void setUp() {
        when(providerType.getDefaultParameterMappings()).thenReturn(Map.of(
            ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-tokens"),
            ModelParameter.REASONING, DefaultParameterMapping.unsupported()
        ));
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
