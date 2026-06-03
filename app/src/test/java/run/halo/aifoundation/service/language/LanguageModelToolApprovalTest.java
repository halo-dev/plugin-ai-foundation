package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolDefinition;

class LanguageModelToolApprovalTest extends LanguageModelTestSupport {

    @Test
    void generateTextReturnsApprovalRequestWithoutExecutingTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "run", "{\"command\":\"rm file\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Remove file")
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(true)
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolApprovalRequests()).singleElement()
                    .satisfies(approval -> {
                        assertThat(approval.getApprovalId()).isEqualTo("approval_call_1");
                        assertThat(approval.getToolName()).isEqualTo("run");
                    });
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT, ModelMessageRole.ASSISTANT);
                assertThat(result.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .map(ModelMessagePart::getType))
                    .containsExactly(PartType.TOOL_CALL, PartType.TOOL_APPROVAL_REQUEST);
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
    }

    @Test
    void generateTextExecutesApprovedToolBeforeProviderContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Removed.", "stop", 4, 5));
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .messages(approvalMessages(true, "User confirmed"))
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(true)
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("removed", context.getInput().get("command")));
                })
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Removed.");
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.TOOL, ModelMessageRole.ASSISTANT);
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
    }

    @Test
    void generateTextStopsAfterApprovalBeforeLaterUnknownTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "run",
                    "{\"command\":\"rm file\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "unknown", "{}")
            ), 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(true)
                .executor(context -> Mono.just(Map.of("ok", true)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolCalls()).singleElement()
                    .satisfies(call -> assertThat(call.getToolCallId()).isEqualTo("call_1"));
                assertThat(result.getToolApprovalRequests()).hasSize(1);
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .map(ModelMessagePart::getToolCallId))
                    .containsExactly("call_1", "call_1");
            })
            .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateTextPreservesApprovalStepIndexInContentAndResponseMessages() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            toolCallResponse("call_2", "run", "{\"command\":\"rm file\"}", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(
                repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22))),
                ToolDefinition.builder()
                    .name("run")
                    .needsApproval(true)
                    .executor(context -> Mono.just(Map.of("ok", true)))
                    .build()
            ))
            .stopWhen(StopCondition.stepCountIs(3))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolApprovalRequests()).singleElement()
                    .satisfies(approval -> assertThat(approval.getStepIndex()).isEqualTo(1));
                assertThat(result.getContent().stream()
                    .filter(part -> PartType.TOOL_APPROVAL_REQUEST.equals(part.getType()))
                    .map(part -> part.getStepIndex()))
                    .containsExactly(1);
                assertThat(result.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .filter(part -> PartType.TOOL_APPROVAL_REQUEST.equals(part.getType()))
                    .map(ModelMessagePart::getStepIndex))
                    .containsExactly(1);
            })
            .verifyComplete();
    }

    @Test
    void streamTextFinalResultPreservesApprovalStepIndexInContentAndResponseMessages() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)),
            Flux.just(toolCallResponse("call_2", "run", "{\"command\":\"rm file\"}", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(
                repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22))),
                ToolDefinition.builder()
                    .name("run")
                    .needsApproval(true)
                    .executor(context -> Mono.just(Map.of("ok", true)))
                    .build()
            ))
            .stopWhen(StopCondition.stepCountIs(3))
            .build();
        var stream = model.streamText(request);

        StepVerifier.create(stream.fullStream().collectList())
            .assertNext(parts -> assertThat(parts.stream()
                .filter(part -> PartType.TOOL_APPROVAL_REQUEST.equals(part.getType()))
                .map(part -> part.getStepIndex()))
                .containsExactly(1))
            .verifyComplete();
        StepVerifier.create(stream.result())
            .assertNext(result -> {
                assertThat(result.getToolApprovalRequests()).singleElement()
                    .satisfies(approval -> assertThat(approval.getStepIndex()).isEqualTo(1));
                assertThat(result.getContent().stream()
                    .filter(part -> PartType.TOOL_APPROVAL_REQUEST.equals(part.getType()))
                    .map(part -> part.getStepIndex()))
                    .containsExactly(1);
                assertThat(result.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .filter(part -> PartType.TOOL_APPROVAL_REQUEST.equals(part.getType()))
                    .map(ModelMessagePart::getStepIndex))
                    .containsExactly(1);
            })
            .verifyComplete();
    }

    @Test
    void generateTextUsesPersistedApprovalStepIndexWhenResumingApprovedTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Removed.", "stop", 4, 5));
        var model = new LanguageModelImpl(chatModel, "openai");
        var observedStepIndex = new AtomicReference<Integer>();

        var request = GenerateTextRequest.builder()
            .messages(approvalMessages(true, "User confirmed", 2))
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(true)
                .executor(context -> {
                    observedStepIndex.set(context.getStepIndex());
                    return Mono.just(Map.of("removed", context.getInput().get("command")));
                })
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getToolResults()).hasSize(1))
            .verifyComplete();

        assertThat(observedStepIndex.get()).isEqualTo(2);
    }

    @Test
    void generateTextExposesCancellationTokenToApprovalPredicate() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "run", "{\"command\":\"rm file\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var source = new CancellationSource();
        var observedToken = new AtomicReference<CancellationToken>();

        var request = GenerateTextRequest.builder()
            .prompt("Remove file")
            .cancellationToken(source.token())
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(context -> {
                    observedToken.set(context.getCancellationToken());
                    return true;
                })
                .executor(context -> Mono.just(Map.of("ok", true)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getToolApprovalRequests()).hasSize(1))
            .verifyComplete();

        assertThat(observedToken.get()).isSameAs(source.token());
    }
}
