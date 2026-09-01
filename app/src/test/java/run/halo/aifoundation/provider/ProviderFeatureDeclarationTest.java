package run.halo.aifoundation.provider;

import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;

import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;

import run.halo.aifoundation.provider.ollama.OllamaProvider;

import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.gitee.GiteeProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.mimo.MiMoProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;

import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;

import run.halo.aifoundation.provider.openai.OpenAiProvider;

import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;

import static org.assertj.core.api.Assertions.assertThat;

import run.halo.aifoundation.provider.ernie.ErnieProvider;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

class ProviderFeatureDeclarationTest {

    @Test
    void everyLanguageProviderDeclaresFeaturesExplicitly() {
        for (var provider : providers()) {
            if (!provider.getSupportedModelTypes().contains(ModelType.LANGUAGE)) {
                continue;
            }
            assertThat(provider.getSupportedFeatures())
                .as(provider.getProviderType())
                .isNotEmpty();
            provider.getSupportedAdapterTypes().stream()
                .filter(adapter -> adapter.getModelType() == ModelType.LANGUAGE)
                .forEach(adapter -> {
                    assertThat(provider.getSupportedFeatures(adapter))
                        .as(provider.getProviderType() + "/" + adapter.getValue())
                        .isNotEmpty();
                    assertThat(provider.getSupportedFeatures())
                        .containsAll(provider.getSupportedFeatures(adapter));
                });
        }
    }

    @Test
    void providerDeclarationsDoNotInheritUndocumentedModalities() {
        assertThat(new DeepSeekProvider().getSupportedFeatures())
            .contains(ModelFeature.STREAMING, ModelFeature.VISION, ModelFeature.TOOL_CALL,
                ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING)
            .doesNotContain(ModelFeature.AUDIO_INPUT);
        assertThat(new MiniMaxProvider().getSupportedFeatures())
            .containsExactly(ModelFeature.STREAMING, ModelFeature.VISION,
                ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING)
            .doesNotContain(ModelFeature.AUDIO_INPUT);
        assertThat(new MiMoProvider().getSupportedFeatures(AdapterType.MIMO_RESPONSES))
            .doesNotContain(ModelFeature.AUDIO_INPUT);
        assertThat(new MiMoProvider().getSupportedFeatures(AdapterType.MIMO_CHAT))
            .contains(ModelFeature.AUDIO_INPUT);
    }

    @Test
    void incompatibleAdapterHasNoCapabilities() {
        assertThat(new DeepSeekProvider().getSupportedFeatures(AdapterType.OPENAI_CHAT)).isEmpty();
    }

    private List<AiProviderType> providers() {
        return List.of(
            new OpenAiProvider(), new OpenAiLikeProvider(), new AiHubMixProvider(),
            new DeepSeekProvider(), new SiliconFlowProvider(), new DouBaoProvider(),
            new ErnieProvider(), new ZhiPuProvider(), new OllamaProvider(),
            new MiniMaxProvider(), new KimiProvider(), new OpenRouterProvider(),
            new DashScopeProvider(), new GiteeProvider(), new MiMoProvider()
        );
    }
}
