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
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolResult;

class LanguageModelExternalToolTest extends LanguageModelTestSupport {

    @Test
    void generateTextReturnsPendingExternalToolCallWhenToolHasNoExecutor() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = languageModel(chatModel, "openai");

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
        var model = languageModel(chatModel, "openai");

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
    void generateTextExecutesServerToolInMixedBatchWithoutInternalContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "weather",
                    "{\"location\":\"SF\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "search",
                    "{\"query\":\"Halo\"}")
            ), 2, 3)
        );
        var model = languageModel(chatModel, "openai");
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
                assertThat(result.getToolCalls())
                    .extracting(ToolCall::getToolCallId)
                    .containsExactly("call_1", "call_2");
                assertThat(result.getToolResults())
                    .extracting(ToolResult::getToolCallId)
                    .containsExactly("call_1");
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT, ModelMessageRole.TOOL);
                assertThat(result.getResponseMessages().getFirst().getContent())
                    .extracting(ModelMessagePart::getToolCallId)
                    .containsExactly("call_1", "call_2");
                assertThat(result.getResponseMessages().getLast().getContent())
                    .singleElement()
                    .satisfies(part -> {
                        assertThat(part.getType()).isEqualTo(PartType.TOOL_RESULT);
                        assertThat(part.getToolCallId()).isEqualTo("call_1");
                    });
                assertThat(result.getWarnings()).extracting("code")
                    .contains("external-tool-pending");
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateTextClassifiesAllExternalAndServerToolsRegardlessOfOrder() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "search",
                    "{\"query\":\"Halo\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "weather",
                    "{\"location\":\"SF\"}"),
                new AssistantMessage.ToolCall("call_3", "function", "beginDraft", "{}")
            ), 2, 3)
        );
        var model = languageModel(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(
                ToolDefinition.builder().name("search").build(),
                ToolDefinition.builder()
                    .name("weather")
                    .executor(context -> {
                        executions.incrementAndGet();
                        return Mono.just(Map.of("temperature", 22));
                    })
                    .build(),
                ToolDefinition.builder().name("beginDraft").build()
            ))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(1);
                assertThat(result.getToolCalls())
                    .extracting(ToolCall::getToolCallId)
                    .containsExactly("call_1", "call_2", "call_3");
                assertThat(result.getToolResults())
                    .extracting(ToolResult::getToolCallId)
                    .containsExactly("call_2");
                assertThat(result.getWarnings().stream()
                    .filter(warning -> "external-tool-pending".equals(warning.getCode())))
                    .hasSize(2);
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateTextContinuesMixedBatchWithoutReexecutingServerTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "weather",
                    "{\"location\":\"SF\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "search",
                    "{\"query\":\"Halo\"}")
            ), 2, 3),
            chatResponse("Finished.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");
        var executions = new AtomicInteger();
        var tools = List.of(
            ToolDefinition.builder()
                .name("weather")
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("temperature", 22));
                })
                .build(),
            ToolDefinition.builder().name("search").build()
        );

        var firstRequest = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(tools)
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(firstRequest)
                .flatMap(firstResult -> {
                    assertThat(firstResult.getToolResults())
                        .extracting(ToolResult::getToolCallId)
                        .containsExactly("call_1");

                    var continuationMessages = new ArrayList<ModelMessage>();
                    continuationMessages.add(ModelMessage.user("Use tools"));
                    continuationMessages.addAll(firstResult.getResponseMessages());
                    continuationMessages.add(ModelMessage.tool(List.of(
                        ModelMessagePart.toolResult(ToolResult.builder()
                            .toolCallId("call_2")
                            .toolName("search")
                            .result(Map.of("matches", 3))
                            .build())
                    )));
                    return model.generateText(GenerateTextRequest.builder()
                        .messages(continuationMessages)
                        .tools(tools)
                        .build());
                }))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Finished.");
                assertThat(result.getToolResults()).isEmpty();
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}
