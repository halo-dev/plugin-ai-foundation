package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;

class LanguageModelRuntimeFactoryTest extends LanguageModelTestSupport {

    private final LanguageModelRuntimeFactory factory =
        new LanguageModelRuntimeFactory(new LanguageModelRuntimeSupport());

    @Test
    void create_passesProviderTypeIntoRuntimeMetadata() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 1, 1));

        var model = factory.create(chatModel, "openai", LanguageModelProviderOptions.defaults());

        StepVerifier.create(model.generateText("Hello"))
            .assertNext(result -> assertThat(result.getRequest().getMetadata())
                .containsEntry("providerType", "openai"))
            .verifyComplete();
    }

    @Test
    void create_appliesProviderOptionsAtCompositionBoundary() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 1, 1));
        var model = factory.create(chatModel, "deepseek", reasoningHistoryProviderOptions());

        var request = GenerateTextRequest.builder()
            .messages(List.of(ModelMessage.assistant(List.of(
                ModelMessagePart.reasoning("thinking")
            ))))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();
    }
}
