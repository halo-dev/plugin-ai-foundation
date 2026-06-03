package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolExecutionContext;

class LanguageModelToolLoopTest extends LanguageModelTestSupport {

    @Test
    void generateTextExecutesToolAndContinuesWhenStopConditionAllows() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var toolContext = new AtomicReference<ToolExecutionContext>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .description("Get weather")
                .inputSchema(Map.of("type", "object"))
                .executor(context -> {
                    toolContext.set(context);
                    return Mono.just(Map.of(
                        "location", context.getInput().get("location"),
                        "temperature", 22
                    ));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getSteps().getFirst().getToolCalls()).hasSize(1);
                assertThat(result.getSteps().getFirst().getToolResults()).hasSize(1);
                assertThat(result.getToolErrors()).isEmpty();
            })
            .verifyComplete();

        assertThat(toolContext.get().getStepIndex()).isZero();
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .contains("tool");
    }

    @Test
    void generateTextProvidesPriorToolHistoryToLaterToolExecutions() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            toolCallResponse("call_2", "weather", "{\"location\":\"NYC\"}", 4, 5),
            chatResponse("Done", "stop", 6, 7)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var contexts = new ArrayList<ToolExecutionContext>();

        var request = GenerateTextRequest.builder()
            .prompt("Compare weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> {
                    contexts.add(context);
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(3))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolCalls()).hasSize(2);
                assertThat(result.getToolResults()).hasSize(2);
                assertThat(result.getText()).isEqualTo("Done");
            })
            .verifyComplete();

        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(1).getStepIndex()).isEqualTo(1);
        assertThat(contexts.get(1).getMessages())
            .extracting(ModelMessage::getRole)
            .contains(ModelMessageRole.ASSISTANT, ModelMessageRole.TOOL);
        assertThat(contexts.get(1).getMessages().stream()
            .flatMap(message -> message.getContent().stream())
            .map(part -> part.getType()))
            .contains(PartType.TOOL_CALL, PartType.TOOL_RESULT);
    }

    @Test
    void generateTextRunsLocalValidationWhenStrictOrExamplesAreUnsupportedByProvider() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Weather?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .inputExamples(List.of(Map.of("location", "SF")))
                .strict(true)
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("temperature", 22));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getToolErrors()).singleElement()
                    .satisfies(error -> assertThat(error.getErrorText())
                        .contains("$.weather.input"));
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
    }

    @Test
    void generateTextExposesCancellationTokenToToolExecutor() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("Done", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var source = new CancellationSource();
        var observedToken = new AtomicReference<CancellationToken>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather?")
            .cancellationToken(source.token())
            .tools(List.of(repairableWeatherTool(context -> {
                observedToken.set(context.getCancellationToken());
                return Mono.just(Map.of("temperature", 22));
            })))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getToolErrors()).isEmpty())
            .verifyComplete();

        assertThat(observedToken.get()).isSameAs(source.token());
    }
}
