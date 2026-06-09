package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.tool.ToolDefinition;

class ProviderNativeToolStrictTest {

    @ParameterizedTest
    @MethodSource("nativeStrictProviders")
    void rc1OpenAiProvidersCannotApplyLegacyNativeStrictToolSchema(AiProviderType providerType) {
        var options = (OpenAiCompatibleChatOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        assertThat(options.getToolCallbacks()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("providersWithoutDocumentedNativeStrict")
    void providersWithoutDocumentedNativeStrictDoNotApplyNativeStrict(AiProviderType providerType) {
        var options = providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        if (options instanceof OpenAiCompatibleChatOptions openAiOptions) {
            assertThat(openAiOptions.getToolCallbacks()).isEmpty();
        }
    }

    @Test
    void ollamaDoesNotExposeNativeStrictToolSchema() {
        var options = new OllamaProvider().languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        assertThat(options).isNotInstanceOf(OpenAiCompatibleChatOptions.class);
    }

    @Test
    void deepSeekDedicatedProviderAppliesNativeStrictToolSchema() {
        var options = (DeepSeekChatOptions) new DeepSeekProvider().languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(strictRequest(), List.of(), Set.of());

        assertThat(options.getTools()).singleElement()
            .satisfies(tool -> assertThat(tool.getFunction().getStrict()).isTrue());
    }

    static Stream<AiProviderType> nativeStrictProviders() {
        return Stream.of(
            new OpenAiProvider(),
            new OpenAiLikeProvider()
        );
    }

    static Stream<AiProviderType> providersWithoutDocumentedNativeStrict() {
        return Stream.of(
            new AiHubMixProvider(),
            new DouBaoProvider(),
            new ErnieProvider(),
            new KimiProvider(),
            new MiniMaxProvider(),
            new SiliconFlowProvider(),
            new XiaomiMiMoProvider(),
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
