package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import run.halo.aifoundation.tool.ToolDefinition;

class LanguageModelRuntimeFactoryTest extends LanguageModelTestSupport {

    private final LanguageModelRuntimeFactory factory =
        new LanguageModelRuntimeFactory(new LanguageModelRuntimeSupport());

    @Test
    void create_passesProviderTypeIntoRuntimeMetadata() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 1, 1));

        var model = factory.create(chatModel, configuration("openai",
            LanguageModelProviderOptions.defaults()));

        StepVerifier.create(model.generateText("Hello"))
            .assertNext(result -> assertThat(result.getRequest().getMetadata())
                .containsEntry("providerType", "openai"))
            .verifyComplete();
    }

    @Test
    void create_appliesProviderOptionsAtCompositionBoundary() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 1, 1));
        var model = factory.create(chatModel, configuration("deepseek",
            reasoningHistoryProviderOptions()));

        var request = GenerateTextRequest.builder()
            .messages(List.of(ModelMessage.assistant(List.of(
                ModelMessagePart.reasoning("thinking")
            ))))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();
    }

    @Test
    void create_appliesConfiguredMultiStepSafetyLimit() {
        var properties = new LanguageModelRuntimeProperties();
        properties.setMaxSteps(2);
        var configuredFactory = new LanguageModelRuntimeFactory(
            new LanguageModelRuntimeSupport(),
            new MediaResourcePolicy(),
            new ModelCapabilityMatcher(),
            properties
        );
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "research", "{}", 1, 1),
            toolCallResponse("call_2", "research", "{}", 1, 1)
        );
        var model = configuredFactory.create(chatModel, configuration("openai",
            LanguageModelProviderOptions.defaults()));

        var request = GenerateTextRequest.builder()
            .prompt("Research")
            .tools(List.of(ToolDefinition.builder()
                .name("research")
                .executor(context -> Mono.just(Map.of("ok", true)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(3))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getWarnings())
                    .extracting("code")
                    .contains("stop-condition-reached");
            })
            .verifyComplete();
    }

    @Test
    void runtimePropertiesValidateConfiguredLimit() {
        var properties = new LanguageModelRuntimeProperties();

        assertThat(properties.getMaxSteps()).isEqualTo(32);
        properties.setMaxSteps(16);
        assertThat(properties.getMaxSteps()).isEqualTo(16);
        assertThatThrownBy(() -> properties.setMaxSteps(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 64");
        assertThatThrownBy(() -> properties.setMaxSteps(65))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 64");
    }

    private LanguageModelRuntimeConfiguration configuration(String providerType,
        LanguageModelProviderOptions providerOptions) {
        return new LanguageModelRuntimeConfiguration(
            ModelRuntimeContext.unresolved(providerType), providerOptions, null);
    }
}
