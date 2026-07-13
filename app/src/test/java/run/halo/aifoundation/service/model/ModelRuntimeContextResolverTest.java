package run.halo.aifoundation.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class ModelRuntimeContextResolverTest {

    @Test
    void resolvesSafeIdentityAndExecutableMappingsWithoutRetainingSecret() {
        var providerType = mock(AiProviderType.class);
        when(providerType.getDefaultParameterMappings()).thenReturn(Map.of(
            ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-completion-tokens")
        ));
        var resolver = new ModelRuntimeContextResolver(new EffectiveParameterMappingResolver(),
            new ParameterMappingTemplateRegistry());

        var context = resolver.resolve(resolution(providerType));

        assertThat(context.modelName()).isEqualTo("model-a");
        assertThat(context.modelId()).isEqualTo("gpt-a");
        assertThat(context.providerName()).isEqualTo("provider-a");
        assertThat(context.providerType()).isEqualTo("openai");
        assertThat(context.providerDefinition()).isSameAs(providerType);
        assertThat(context.parameterMappings().get(ModelParameter.MAX_OUTPUT_TOKENS).template())
            .isEqualTo("openai.max-completion-tokens");
        assertThat(Arrays.stream(ModelRuntimeContext.class.getRecordComponents())
            .map(component -> component.getName().toLowerCase()))
            .noneMatch(name -> name.contains("key") || name.contains("secret")
                || name.equals("model") || name.equals("provider"));
    }

    private ModelResolution resolution(AiProviderType providerType) {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        var providerSpec = new AiProvider.AiProviderSpec();
        providerSpec.setProviderType("openai");
        provider.setSpec(providerSpec);

        var model = new AiModel();
        model.setMetadata(metadata("model-a"));
        var modelSpec = new AiModel.AiModelSpec();
        modelSpec.setProviderName("provider-a");
        modelSpec.setModelId("gpt-a");
        modelSpec.setModelType(ModelType.LANGUAGE);
        model.setSpec(modelSpec);
        return new ModelResolution(model, provider, providerType, "do-not-retain");
    }

    private Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
