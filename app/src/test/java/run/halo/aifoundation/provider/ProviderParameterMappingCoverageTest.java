package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;

class ProviderParameterMappingCoverageTest {

    private final ParameterMappingTemplateRegistry registry =
        new ParameterMappingTemplateRegistry();

    @ParameterizedTest(name = "{0}")
    @MethodSource("providers")
    void builtInMappingsReferenceCompatibleTemplates(AiProviderType providerType) {
        var adapters = providerType.getSupportedAdapterTypes();
        providerType.getDefaultParameterMappings().forEach((parameter, mapping) -> {
            if (mapping.mode()
                == run.halo.aifoundation.extension.ModelParameterMappings.Mode.UNSUPPORTED) {
                assertThat(mapping.template()).isNull();
                return;
            }
            var templateId = mapping.template();
            var descriptor = registry.get(templateId);
            assertThat(descriptor)
                .as("template %s for %s", templateId, parameter)
                .isNotNull();
            assertThat(descriptor.parameter()).isEqualTo(parameter);
            assertThat(descriptor.adapterTypes())
                .anyMatch(adapters::contains);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providers")
    void everySupportedModelDomainHasADeclaration(AiProviderType providerType) {
        var defaults = providerType.getDefaultParameterMappings();
        providerType.getSupportedModelTypes().forEach(modelType ->
            assertThat(defaults.keySet().stream()
                .anyMatch(parameter -> parameter.getModelType() == modelType))
                .as("mapping declaration for %s", modelType)
                .isTrue());
    }

    @Test
    void reasoningDefaultsFollowProviderWireProtocols() {
        assertReasoningTemplate(new DeepSeekProvider(), "reasoning.deepseek");
        assertReasoningTemplate(new OpenRouterProvider(), "reasoning.openrouter");
        assertReasoningTemplate(new DashScopeProvider(), "reasoning.enable-thinking");
        assertReasoningTemplate(new DouBaoProvider(), "reasoning.thinking-type");
        assertReasoningTemplate(new ZhiPuProvider(), "reasoning.thinking-type");
        assertReasoningTemplate(new OllamaProvider(), "reasoning.ollama-think");
        assertThat(new OpenAiLikeProvider().getDefaultParameterMappings()
            .get(ModelParameter.REASONING).mode()).isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
    }

    @Test
    void providerSpecificDefaultsFollowProviderWireProtocols() {
        assertThat(new DashScopeProvider().getDefaultParameterMappings().get(ModelParameter.TOP_N))
            .satisfies(mapping -> {
                assertThat(mapping.mode()).isEqualTo(ModelParameterMappings.Mode.TEMPLATE);
                assertThat(mapping.template()).isEqualTo("rerank.parameters.top-n");
            });
        assertThat(new OpenAiProvider().getDefaultParameterMappings())
            .satisfies(defaults -> {
                assertThat(defaults.get(ModelParameter.MIN_P).mode())
                    .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
                assertThat(defaults.get(ModelParameter.REPETITION_PENALTY).mode())
                    .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
            });
    }

    private void assertReasoningTemplate(AiProviderType providerType, String template) {
        assertThat(providerType.getDefaultParameterMappings().get(ModelParameter.REASONING))
            .satisfies(mapping -> {
                assertThat(mapping.mode()).isEqualTo(ModelParameterMappings.Mode.TEMPLATE);
                assertThat(mapping.template()).isEqualTo(template);
            });
    }

    static List<AiProviderType> providers() {
        return List.of(
            new OpenAiProvider(), new OpenAiLikeProvider(), new AiHubMixProvider(),
            new DeepSeekProvider(), new SiliconFlowProvider(), new DouBaoProvider(),
            new ErnieProvider(), new ZhiPuProvider(), new OllamaProvider(),
            new MiniMaxProvider(), new KimiProvider(), new OpenRouterProvider(),
            new DashScopeProvider(), new GiteeMoArkProvider(), new XiaomiMiMoProvider()
        );
    }
}
