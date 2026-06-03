package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolDefinition;

class LanguageModelExternalToolTest extends LanguageModelTestSupport {

    @Test
    void generateTextReturnsPendingExternalToolCallWhenToolHasNoExecutor() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder().name("weather").build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(1);
                assertThat(result.getToolCalls()).singleElement()
                    .satisfies(call -> assertThat(call.getToolCallId()).isEqualTo("call_1"));
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getResponseMessages()).singleElement()
                    .satisfies(message -> assertThat(message.getContent()).singleElement()
                        .satisfies(part -> assertThat(part.getType())
                            .isEqualTo(PartType.TOOL_CALL)));
                assertThat(result.getWarnings()).extracting("code")
                    .contains("external-tool-pending");
            })
            .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateTextContinuesFromExternalToolResult() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .messages(externalToolResultMessages())
            .tools(List.of(ToolDefinition.builder().name("weather").build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getResponseMessages()).singleElement()
                    .satisfies(message -> assertThat(message.getRole())
                        .isEqualTo(ModelMessageRole.ASSISTANT));
            })
            .verifyComplete();
    }

    @Test
    void generateTextStopsMixedExecutableAndExternalToolCallsWithoutContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "weather",
                    "{\"location\":\"SF\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "search",
                    "{\"query\":\"Halo\"}")
            ), 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(
                ToolDefinition.builder()
                    .name("weather")
                    .executor(context -> {
                        executions.incrementAndGet();
                        return Mono.just(Map.of("temperature", 22));
                    })
                    .build(),
                ToolDefinition.builder().name("search").build()
            ))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(1);
                assertThat(result.getToolCalls()).singleElement()
                    .satisfies(call -> {
                        assertThat(call.getToolCallId()).isEqualTo("call_2");
                        assertThat(call.getToolName()).isEqualTo("search");
                    });
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getResponseMessages()).singleElement()
                    .satisfies(message -> assertThat(message.getContent()).singleElement()
                        .satisfies(part -> assertThat(part.getToolCallId()).isEqualTo("call_2")));
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
        verify(chatModel).call(any(Prompt.class));
    }
}
