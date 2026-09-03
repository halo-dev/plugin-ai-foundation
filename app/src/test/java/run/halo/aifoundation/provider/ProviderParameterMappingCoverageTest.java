package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;
import run.halo.aifoundation.provider.ernie.ErnieProvider;
import run.halo.aifoundation.provider.gitee.GiteeProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ModelParameterCatalog;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.mimo.MiMoProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.ollama.OllamaProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;

class ProviderParameterMappingCoverageTest {

    private final ParameterMappingTemplateRegistry registry =
        new ParameterMappingTemplateRegistry();
    private final ModelParameterCatalog catalog = new ModelParameterCatalog();

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
    void adapterReasoningDefaultsMatchTheSelectedWireProtocol(AiProviderType providerType) {
        providerType.getSupportedAdapterTypes().stream()
            .filter(adapter -> adapter.getModelType()
                == run.halo.aifoundation.provider.support.ModelType.LANGUAGE)
            .forEach(adapter -> {
                var mapping = providerType.getDefaultParameterMappings(adapter)
                    .get(ModelParameter.REASONING);
                if (mapping.mode() == ModelParameterMappings.Mode.UNSUPPORTED) {
                    assertThat(mapping.template()).isNull();
                    return;
                }
                assertThat(registry.get(mapping.template()).adapterTypes())
                    .as("%s reasoning mapping for %s", providerType.getProviderType(), adapter)
                    .contains(adapter);
            });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providers")
    void everySupportedModelDomainHasCompleteDeclarations(AiProviderType providerType) {
        var defaults = providerType.getDefaultParameterMappings();
        var expected = catalog.definitionsFor(providerType.getSupportedModelTypes()).stream()
            .map(definition -> definition.parameter())
            .toList();

        assertThat(defaults.keySet())
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void reasoningDefaultsFollowCurrentProviderProtocols() {
        var expectedTemplates = Map.ofEntries(
            Map.entry("openai", "reasoning.responses-effort"),
            Map.entry("aihubmix", "reasoning.responses-effort"),
            Map.entry("deepseek", "reasoning.deepseek"),
            Map.entry("siliconflow", "reasoning.enable-thinking"),
            Map.entry("doubao", "reasoning.thinking-type"),
            Map.entry("ernie", "reasoning.thinking-type"),
            Map.entry("zhipuai", "reasoning.zhipu"),
            Map.entry("ollama", "reasoning.ollama-think"),
            Map.entry("minimax", "reasoning.minimax"),
            Map.entry("kimi", "reasoning.kimi"),
            Map.entry("openrouter", "reasoning.openrouter"),
            Map.entry("dashscope", "reasoning.dashscope"),
            Map.entry("mimo", "reasoning.responses-effort")
        );

        providers().forEach(providerType -> {
            var mapping = providerType.getDefaultParameterMappings()
                .get(ModelParameter.REASONING);
            var expected = expectedTemplates.get(providerType.getProviderType());
            if (expected == null) {
                assertThat(mapping.mode()).as(providerType.getProviderType())
                    .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
                return;
            }
            assertThat(mapping.mode()).as(providerType.getProviderType())
                .isEqualTo(ModelParameterMappings.Mode.TEMPLATE);
            assertThat(mapping.template()).as(providerType.getProviderType())
                .isEqualTo(expected);
        });
    }

    @Test
    void providerSpecificDefaultsFollowProviderWireProtocols() {
        var dashScope = new DashScopeProvider();
        assertThat(dashScope.getDefaultParameterMappings(AdapterType.DASHSCOPE_COMPATIBLE_RERANK)
            .get(ModelParameter.TOP_N))
            .satisfies(mapping -> {
                assertThat(mapping.mode()).isEqualTo(ModelParameterMappings.Mode.TEMPLATE);
                assertThat(mapping.template()).isEqualTo("rerank.top-n");
            });
        assertThat(dashScope.getDefaultParameterMappings(AdapterType.DASHSCOPE_NATIVE_RERANK)
            .get(ModelParameter.TOP_N))
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

    @Test
    void reasoningDefaultsFollowSelectedAdapterWireProtocol() {
        assertThat(new OpenAiProvider()
            .getDefaultParameterMappings(AdapterType.OPENAI_RESPONSES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.responses-effort");
        assertThat(new OpenAiProvider()
            .getDefaultParameterMappings(AdapterType.OPENAI_CHAT)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.effort");
        assertThat(new DashScopeProvider()
            .getDefaultParameterMappings(AdapterType.DASHSCOPE_RESPONSES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.responses-effort");
        assertThat(new DashScopeProvider()
            .getDefaultParameterMappings(AdapterType.DASHSCOPE_MESSAGES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.dashscope-messages");
        assertThat(new DeepSeekProvider()
            .getDefaultParameterMappings(AdapterType.DEEPSEEK_MESSAGES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.deepseek-messages");
        assertThat(new MiniMaxProvider()
            .getDefaultParameterMappings(AdapterType.MINIMAX_RESPONSES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.responses-effort");
        assertThat(new OpenRouterProvider()
            .getDefaultParameterMappings(AdapterType.OPENROUTER_MESSAGES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.openrouter-messages");
        assertThat(new MiMoProvider()
            .getDefaultParameterMappings(AdapterType.MIMO_CHAT)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.thinking-type");
        assertThat(new MiMoProvider()
            .getDefaultParameterMappings(AdapterType.MIMO_MESSAGES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.messages-thinking");
        assertThat(new OllamaProvider()
            .getDefaultParameterMappings(AdapterType.OLLAMA_CHAT)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.ollama-think");
        assertThat(new OllamaProvider()
            .getDefaultParameterMappings(AdapterType.OLLAMA_MESSAGES)
            .get(ModelParameter.REASONING).template())
            .isEqualTo("reasoning.messages-thinking");
    }

    static List<AiProviderType> providers() {
        return List.of(
            new OpenAiProvider(), new OpenAiLikeProvider(), new AiHubMixProvider(),
            new DeepSeekProvider(), new SiliconFlowProvider(), new DouBaoProvider(),
            new ErnieProvider(), new ZhiPuProvider(), new OllamaProvider(),
            new MiniMaxProvider(), new KimiProvider(), new OpenRouterProvider(),
            new DashScopeProvider(), new GiteeProvider(), new MiMoProvider()
        );
    }
}
