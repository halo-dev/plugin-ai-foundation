package run.halo.aifoundation.provider.protocol.chatcompletions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

class ChatCompletionsOptionsFactoryTest {

    @Test
    void appliesOneProviderConfigurationAcrossRequestKinds() {
        var factory = ChatCompletionsOptionsFactory
            .builder("example", ReasoningControlOptions.unsupported())
            .extraBodyCustomizer((body, request) -> body.put("customized", true))
            .structuredOutputSupport(StructuredOutputSupport.JSON_OBJECT)
            .build();
        var request = GenerateTextRequest.builder()
            .temperature(0.5)
            .topLogprobs(3)
            .headers(Map.of("X-Request", "value"))
            .build();

        var nativeOptions = Map.<String, Object>of("provider-field", "value");
        var basic = applyNativeOptions(factory.basic(request), nativeOptions);
        var structured = applyNativeOptions(factory.structured(request), nativeOptions);
        var toolCalling = applyNativeOptions(
            factory.toolCalling(request, java.util.List.of(), java.util.Set.of()), nativeOptions);

        assertThat(basic.getTemperature()).isEqualTo(0.5);
        assertThat(basic.getLogprobs()).isTrue();
        assertThat(basic.getTopLogprobs()).isEqualTo(3);
        assertThat(basic.getCustomHeaders()).containsEntry("X-Request", "value");
        assertThat(basic.getExtraBody()).containsEntry("provider-field", "value")
            .containsEntry("customized", true);
        assertThat(structured.getExtraBody()).isEqualTo(basic.getExtraBody());
        assertThat(toolCalling.getExtraBody()).isEqualTo(basic.getExtraBody());
    }

    private ChatCompletionsOptions applyNativeOptions(ChatCompletionsOptions options,
        Map<String, Object> nativeOptions) {
        return (ChatCompletionsOptions) ChatCompletionsNativeOptions.apply(options, nativeOptions);
    }

    @Test
    void keepsExplicitLogprobsValue() {
        var factory = ChatCompletionsOptionsFactory
            .builder("example", ReasoningControlOptions.unsupported())
            .build();
        var request = GenerateTextRequest.builder()
            .logprobs(false)
            .topLogprobs(3)
            .build();

        assertThat(factory.basic(request).getLogprobs()).isFalse();
    }

    @Test
    void rejectsIncompleteProviderConfiguration() {
        assertThatThrownBy(() -> ChatCompletionsOptionsFactory
            .builder(" ", ReasoningControlOptions.unsupported()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Provider type must not be blank");
        assertThatThrownBy(() -> ChatCompletionsOptionsFactory.builder("example", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Reasoning control options must not be null");
    }
}
