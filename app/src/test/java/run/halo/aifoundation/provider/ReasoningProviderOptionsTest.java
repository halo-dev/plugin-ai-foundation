package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;

class ReasoningProviderOptionsTest {

    @Test
    void aiHubMixOptions_applyTypedReasoningEffort() {
        var options = openAiOptions(new AiHubMixProvider(), GenerateTextRequest.builder()
            .prompt("Think carefully")
            .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.MEDIUM))
            .build());

        assertThat(options.getReasoningEffort()).isEqualTo("medium");
    }

    @Test
    void thinkingTypeProviders_applyTypedDisabledReasoning() {
        assertThinkingType(new KimiProvider(), "kimi");
        assertThinkingType(new DouBaoProvider(), "doubao");
        assertThinkingType(new ZhiPuProvider(), "zhipuai");
        assertThinkingType(new XiaomiMiMoProvider(), "mimo");
    }

    @Test
    void enableThinkingProviders_applyTypedDisabledReasoning() {
        assertEnableThinking(new ErnieProvider(), "ernie");
        assertEnableThinking(new SiliconFlowProvider(), "siliconflow");
    }

    @Test
    void ollamaOptions_applyBooleanAndEffortReasoningControls() {
        var disabledOptions = (OllamaChatOptions) new OllamaProvider()
            .languageModelProviderOptions()
            .chatOptionsFactory()
            .build(GenerateTextRequest.builder()
                .prompt("Fast")
                .reasoning(ReasoningOptions.disabled())
            .build());

        assertThat(disabledOptions.getThinkOption().toJsonValue()).isEqualTo(false);
        assertThat(disabledOptions.getSeed()).isNull();

        var effortOptions = (OllamaChatOptions) new OllamaProvider()
            .languageModelProviderOptions()
            .chatOptionsFactory()
            .build(GenerateTextRequest.builder()
                .prompt("Think")
                .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
                .seed(99)
                .build());

        assertThat(effortOptions.getThinkOption().toJsonValue()).isEqualTo("high");
        assertThat(effortOptions.getSeed()).isEqualTo(99);
    }

    @Test
    void minimaxOptions_keepTypedReasoningUnsupported() {
        var providerType = new MiniMaxProvider();
        var options = openAiOptions(providerType, GenerateTextRequest.builder()
            .prompt("Generate")
            .build());

        assertThat(options.getExtraBody()).isNullOrEmpty();
        assertThatThrownBy(() -> providerType.languageModelProviderOptions()
            .reasoningControlOptions()
            .validate("minimax", GenerateTextRequest.builder()
                .prompt("Fast")
                .reasoning(ReasoningOptions.disabled())
                .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("disabled reasoning is not supported by provider type: minimax");
    }

    private OpenAiCompatibleChatOptions openAiOptions(AiProviderType providerType,
        GenerateTextRequest request) {
        return (OpenAiCompatibleChatOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory()
            .build(request);
    }

    private void assertThinkingType(AiProviderType providerType, String providerKey) {
        var options = openAiOptions(providerType, GenerateTextRequest.builder()
            .prompt("Fast")
            .reasoning(ReasoningOptions.disabled())
            .build());

        assertThat(options.getExtraBody())
            .containsEntry("thinking", Map.of("type", "disabled"));
    }

    private void assertEnableThinking(AiProviderType providerType, String providerKey) {
        var options = openAiOptions(providerType, GenerateTextRequest.builder()
            .prompt("Fast")
            .reasoning(ReasoningOptions.disabled())
            .build());

        assertThat(options.getExtraBody())
            .containsEntry("enable_thinking", false);
    }
}
