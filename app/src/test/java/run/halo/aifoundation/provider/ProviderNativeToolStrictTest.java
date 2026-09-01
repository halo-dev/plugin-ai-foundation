package run.halo.aifoundation.provider;

import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;

import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;

import run.halo.aifoundation.provider.ollama.OllamaProvider;

import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.mimo.MiMoProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;

import run.halo.aifoundation.provider.openai.OpenAiProvider;

import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;

import static org.assertj.core.api.Assertions.assertThat;

import run.halo.aifoundation.provider.ernie.ErnieProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.tool.ToolDefinition;

class ProviderNativeToolStrictTest {

    @ParameterizedTest
    @MethodSource("nativeStrictProviders")
    void nativeStrictProvidersApplyStrictToolSchema(AiProviderType providerType) {
        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        assertThat(providerType.languageModelProviderOptions().nativeStrictToolSchemas()).isTrue();
        assertThat(options.getToolStrict()).containsEntry("halo_test_info", true);
    }

    @ParameterizedTest
    @MethodSource("providersWithoutDocumentedNativeStrict")
    void providersWithoutDocumentedNativeStrictDoNotApplyNativeStrict(AiProviderType providerType) {
        var options = providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        if (options instanceof ChatCompletionsOptions openAiOptions) {
            assertThat(providerType.languageModelProviderOptions().nativeStrictToolSchemas())
                .isFalse();
            assertThat(openAiOptions.getToolStrict()).isNullOrEmpty();
        }
    }

    @Test
    void ollamaDoesNotExposeNativeStrictToolSchema() {
        var options = new OllamaProvider().languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        assertThat(options).isNotInstanceOf(ChatCompletionsOptions.class);
    }

    @Test
    void deepSeekUsesOpenAiCompatibleToolCallbackContract() {
        var options = (ChatCompletionsOptions) new DeepSeekProvider()
            .languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        assertThat(new DeepSeekProvider().languageModelProviderOptions()
            .nativeStrictToolSchemas()).isTrue();
        assertThat(options.getToolStrict()).containsEntry("halo_test_info", true);
    }

    static Stream<AiProviderType> nativeStrictProviders() {
        return Stream.of(
            new OpenAiProvider(),
            new OpenAiLikeProvider(),
            new OpenRouterProvider(),
            new KimiProvider(),
            new SiliconFlowProvider(),
            new MiMoProvider(),
            new AiHubMixProvider()
        );
    }

    static Stream<AiProviderType> providersWithoutDocumentedNativeStrict() {
        return Stream.of(
            new DashScopeProvider(),
            new DouBaoProvider(),
            new ErnieProvider(),
            new MiniMaxProvider(),
            new ZhiPuProvider()
        );
    }

    private static GenerateTextRequest strictRequest() {
        return GenerateTextRequest.builder()
            .prompt("Use a strict tool")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .description("Read Halo test information")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("name", Map.of("type", "string")),
                    "required", List.of("name"),
                    "additionalProperties", false
                ))
                .strict(true)
                .build()))
            .build();
    }
}
