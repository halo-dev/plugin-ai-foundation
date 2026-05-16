package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.aifoundation.ChatRequest;
import run.halo.aifoundation.Message;

class LanguageModelImplTest {

    @Test
    void streamChat_mapsMessagesAndOptionsToSpringPrompt() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = ChatRequest.builder()
            .messages(List.of(
                Message.system("You are concise."),
                Message.user("Hello"),
                Message.assistant("Hi")
            ))
            .temperature(0.2)
            .maxTokens(128)
            .topP(0.9)
            .build();

        StepVerifier.create(model.streamChat(request)).verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());

        var prompt = captor.getValue();
        assertThat(prompt.getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .containsExactly("system", "user", "assistant");
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.2);
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(128);
        assertThat(prompt.getOptions().getTopP()).isEqualTo(0.9);
    }
}
