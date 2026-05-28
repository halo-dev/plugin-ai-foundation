package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;

class OpenAiProviderTest {

    private final OpenAiProvider providerType = new OpenAiProvider();

    @Test
    void options_applyTypedReasoningEffort() {
        var request = GenerateTextRequest.builder()
            .prompt("Think carefully")
            .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
            .seed(42)
            .build();

        var options = (OpenAiChatOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory()
            .build(request);

        assertThat(options.getReasoningEffort()).isEqualTo("high");
        assertThat(options.getSeed()).isEqualTo(42);
    }
}
