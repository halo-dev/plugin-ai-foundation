package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;
import run.halo.aifoundation.provider.ernie.ErnieProvider;
import run.halo.aifoundation.provider.gitee.GiteeProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.mimo.MiMoProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.ollama.OllamaProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;

class ReasoningProviderOptionsTest {

    @Test
    void everyProviderRequiresExplicitModelReasoningMappings() {
        providers().forEach(provider -> {
            var controls = provider.languageModelProviderOptions().reasoningControlOptions();
            assertThat(controls.enabledSupported()).as(provider.getProviderType()).isFalse();
            assertThat(controls.disabledSupported()).as(provider.getProviderType()).isFalse();
            assertThat(controls.supportedEfforts()).as(provider.getProviderType()).isEmpty();
        });
    }

    private List<AiProviderType> providers() {
        return List.of(
            new AiHubMixProvider(),
            new DashScopeProvider(),
            new DeepSeekProvider(),
            new DouBaoProvider(),
            new ErnieProvider(),
            new GiteeProvider(),
            new KimiProvider(),
            new MiMoProvider(),
            new MiniMaxProvider(),
            new OllamaProvider(),
            new OpenAiProvider(),
            new OpenAiLikeProvider(),
            new OpenRouterProvider(),
            new SiliconFlowProvider(),
            new ZhiPuProvider());
    }
}
