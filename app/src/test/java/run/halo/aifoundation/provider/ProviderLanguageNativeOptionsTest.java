package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.GenerateTextRequest;
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
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;
import run.halo.aifoundation.service.language.mapping.LanguageModelChatOptionsBuilder;

class ProviderLanguageNativeOptionsTest {

    @Test
    void providerOwnedLanguageOptionsApplyAdministratorNativeOptions() {
        for (var provider : providers()) {
            assertNativeOptionsApplied(provider.getProviderType(),
                provider.languageModelProviderOptions());
        }
    }

    @Test
    void ollamaCompatibleProtocolsApplyAdministratorNativeOptions() {
        var provider = new OllamaProvider();

        assertNativeOptionsApplied(provider.getProviderType(),
            provider.languageModelProviderOptions(AdapterType.OLLAMA_RESPONSES));
        assertNativeOptionsApplied(provider.getProviderType(),
            provider.languageModelProviderOptions(AdapterType.OLLAMA_MESSAGES));
    }

    @Test
    void rejectsConfiguredNativeOptionsWhenProviderHasNoApplicator() {
        var builder = new LanguageModelChatOptionsBuilder("provider", "configured-model",
            LanguageModelProviderOptions.defaults(), Map.of("extension", true), ignored -> "{}");

        assertThatThrownBy(() -> builder.build(
            GenerateTextRequest.builder().prompt("Hello").build()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not support configured native options");
    }

    private void assertNativeOptionsApplied(String providerType,
        LanguageModelProviderOptions providerOptions) {
        var builder = new LanguageModelChatOptionsBuilder(providerType, "configured-model",
            providerOptions, Map.of("provider_extension", "configured"), ignored -> "{}");

        var options = builder.build(GenerateTextRequest.builder().prompt("Hello").build());

        assertThat(options)
            .as(providerType)
            .isInstanceOf(ChatCompletionsOptions.class);
        assertThat(((ChatCompletionsOptions) options).getExtraBody())
            .as(providerType)
            .containsEntry("provider_extension", "configured");
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
            new OpenAiProvider(),
            new OpenAiLikeProvider(),
            new OpenRouterProvider(),
            new SiliconFlowProvider(),
            new ZhiPuProvider()
        );
    }
}
