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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
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
import run.halo.aifoundation.ui.ToolPart;
import run.halo.aifoundation.ui.ToolPartState;
import run.halo.aifoundation.ui.UIMessage;
import run.halo.aifoundation.ui.UIMessageChatHandlers;
import run.halo.aifoundation.ui.UIMessageParts;
import run.halo.aifoundation.ui.UIMessageRole;
import run.halo.aifoundation.ui.UIMessageStreamFinish;

class LanguageModelToolLoopTest extends LanguageModelTestSupport {

    @Test
    void generateTextExecutesToolAndContinuesWhenStopConditionAllows() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");
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
    void generateTextContinuesAfterUnknownToolError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "missing", "{}", 2, 3),
            chatResponse("Recovered after the tool error.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use a tool")
            .tools(List.of(ToolDefinition.builder()
                .name("available")
                .executor(context -> Mono.just(Map.of("ok", true)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getText()).isEqualTo("Recovered after the tool error.");
                assertThat(result.getToolErrors())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getErrorText())
                        .contains("Unknown tool: missing"));
            })
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .contains("tool");
    }

    @Test
    void generateTextContinuesAfterToolExecutionError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "unstable", "{}", 2, 3),
            chatResponse("Recovered after execution failed.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use a tool")
            .tools(List.of(ToolDefinition.builder()
                .name("unstable")
                .executor(context -> Mono.error(new IllegalStateException("temporary failure")))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getText()).isEqualTo("Recovered after execution failed.");
                assertThat(result.getToolErrors())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getErrorText())
                        .isEqualTo("temporary failure"));
            })
            .verifyComplete();

        var secondPrompt = capturedCallPrompts(chatModel).get(1);
        assertThat(toolResponses(secondPrompt))
            .singleElement()
            .satisfies(response -> {
                assertThat(response.id()).isEqualTo("call_1");
                assertThat(response.name()).isEqualTo("unstable");
                assertThat(response.responseData()).isEqualTo("temporary failure");
            });
    }

    @Test
    void generateTextIncludesResponsesForSuccessfulAndFailedToolCalls() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "unstable", "{}"),
                new AssistantMessage.ToolCall("call_2", "function", "stable", "{}")
            ), 2, 3),
            chatResponse("Handled both tool responses.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use both tools")
            .tools(List.of(
                ToolDefinition.builder()
                    .name("unstable")
                    .executor(context -> Mono.error(new IllegalStateException("quota exceeded")))
                    .build(),
                ToolDefinition.builder()
                    .name("stable")
                    .executor(context -> Mono.just(Map.of("ok", true)))
                    .build()
            ))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getToolErrors()).hasSize(1);
                assertThat(result.getText()).isEqualTo("Handled both tool responses.");
            })
            .verifyComplete();

        var responses = toolResponses(capturedCallPrompts(chatModel).get(1));
        assertThat(responses)
            .extracting(ToolResponseMessage.ToolResponse::id)
            .containsExactlyInAnyOrder("call_1", "call_2");
        assertThat(responses)
            .filteredOn(response -> response.id().equals("call_1"))
            .singleElement()
            .satisfies(response -> assertThat(response.responseData())
                .isEqualTo("quota exceeded"));
    }

    @Test
    void streamTextIncludesFailedToolResponseInNextPrompt() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "generateImage", "{}", 2, 3)),
            Flux.just(chatResponse("The image tool has insufficient balance.", "stop", 4, 5))
        );
        var model = languageModel(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Generate an image")
            .tools(List.of(ToolDefinition.builder()
                .name("generateImage")
                .executor(context -> Mono.error(new IllegalStateException(
                    "OpenAI-compatible image request failed: status=429, body=余额不足")))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).result())
            .assertNext(result -> {
                assertThat(result.getText())
                    .isEqualTo("The image tool has insufficient balance.");
                assertThat(result.getToolErrors()).singleElement()
                    .satisfies(error -> assertThat(error.getErrorText()).contains("status=429"));
            })
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(captor.capture());
        assertThat(toolResponses(captor.getAllValues().get(1)))
            .singleElement()
            .satisfies(response -> {
                assertThat(response.id()).isEqualTo("call_1");
                assertThat(response.name()).isEqualTo("generateImage");
                assertThat(response.responseData())
                    .isEqualTo(
                        "OpenAI-compatible image request failed: status=429, body=余额不足");
            });
    }

    @Test
    void streamTextPreservesTheSubscribingCallersSecurityContextDuringToolExecution() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "readProfile", "{}", 2, 3)),
            Flux.just(chatResponse("Read the current user's profile.", "stop", 4, 5)));
        var model = languageModel(chatModel, "openai");
        var authentication = UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of());
        var observedIdentity = new AtomicReference<Authentication>();
        var executions = new AtomicInteger();
        var request = GenerateTextRequest.builder().prompt("Read my profile")
            .tools(List.of(ToolDefinition.builder().name("readProfile")
                .executor(context -> ReactiveSecurityContextHolder.getContext()
                    .switchIfEmpty(Mono.error(new IllegalStateException("Missing authenticated caller")))
                    .map(security -> {
                        observedIdentity.set(security.getAuthentication());
                        executions.incrementAndGet();
                        return (Object) Map.of("actor", security.getAuthentication().getName());
                    }))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2)).build();
        var stream = model.streamText(request);

        StepVerifier.create(stream.fullStream().collectList()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .assertNext(parts -> assertThat(parts).isNotEmpty()).verifyComplete();
        // Reading the cached result must not execute the authenticated tool a second time.
        StepVerifier.create(stream.result()).assertNext(result -> {
            assertThat(result.getToolErrors()).isEmpty();
            assertThat(result.getToolResults()).hasSize(1);
            assertThat(result.getText()).isEqualTo("Read the current user's profile.");
        }).verifyComplete();
        assertThat(observedIdentity.get()).isSameAs(authentication);
        assertThat(executions).hasValue(1);
    }

    @Test
    void uiMessageChatPreservesTheFirstChunkSubscribersIdentityThroughToolsAndFinishAggregation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_get_post", "get_post", "{\"name\":\"hello\"}", 2, 3)),
            Flux.just(chatResponse("The post was read successfully.", "stop", 4, 5)));
        var model = languageModel(chatModel, "openai");
        var authentication = UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of());
        var observedIdentity = new AtomicReference<Authentication>();
        var executions = new AtomicInteger();
        var tool = ToolDefinition.builder().name("get_post")
            .executor(context -> ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.error(new IllegalStateException("Missing authenticated caller")))
                .map(security -> {
                    observedIdentity.set(security.getAuthentication());
                    executions.incrementAndGet();
                    return (Object) Map.of("name", context.getInput().get("name"), "title", "Hello");
                })).build();
        var user = new UIMessage<>("user-security-context", UIMessageRole.USER,
            List.of(UIMessageParts.text("user-text", "Read the hello post")), Map.<String, Object>of());
        var capturedFinish = new AtomicReference<UIMessageStreamFinish<Map<String, Object>>>();
        var chat = UIMessageChatHandlers.<Map<String, Object>>streamText(options -> options
            .model(model).messages(List.of(user)).metadataSupplier(Map::of)
            .request(request -> request.tools(List.of(tool)).stopWhen(StopCondition.stepCountIs(2)))
            .onFinish(capturedFinish::set));

        // This must be the first subscription: finish aggregation also subscribes internally.
        StepVerifier.create(chat.stream().chunks().collectList()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .assertNext(chunks -> assertThat(chunks).extracting(chunk -> chunk.type())
                .contains("tool-output-available", "finish")
                .doesNotContain("tool-output-error", "tool-input-error", "error"))
            .verifyComplete();
        StepVerifier.create(chat.finish()).assertNext(finish -> {
            assertThat(finish.responseMessage().text()).isEqualTo("The post was read successfully.");
            assertThat(finish.responseMessage().parts()).filteredOn(ToolPart.class::isInstance)
                .singleElement().satisfies(part -> {
                    var output = (ToolPart) part;
                    assertThat(output.toolName()).isEqualTo("get_post");
                    assertThat(output.state()).isEqualTo(ToolPartState.OUTPUT_AVAILABLE);
                    assertThat(output.output()).isEqualTo(Map.of("name", "hello", "title", "Hello"));
                });
            assertThat(capturedFinish.get()).isEqualTo(finish);
        }).verifyComplete();
        assertThat(observedIdentity.get()).isSameAs(authentication);
        assertThat(executions).hasValue(1);
    }

    @Test
    void streamTextPreservesInvalidToolCallWhenContinuingWithInputError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather",
                "{\"location\":\"S\"F\"}", 2, 3)),
            Flux.just(chatResponse("The tool arguments were malformed.", "stop", 4, 5))
        );
        var model = languageModel(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather?")
            .tools(List.of(repairableWeatherTool(context ->
                Mono.just(Map.of("temperature", 22)))))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).result())
            .assertNext(result -> assertThat(result.getToolErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorText())
                    .startsWith("Tool arguments contain malformed JSON at character")))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(captor.capture());
        var secondPrompt = captor.getAllValues().get(1);
        assertThat(secondPrompt.getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .flatMap(message -> message.getToolCalls().stream())
            .map(AssistantMessage.ToolCall::id))
            .contains("call_1");
        assertThat(toolResponses(secondPrompt))
            .filteredOn(response -> "call_1".equals(response.id()))
            .singleElement()
            .satisfies(response -> assertThat(response.responseData())
                .startsWith("Tool arguments contain malformed JSON at character"));
    }

    @Test
    void generateTextProvidesPriorToolHistoryToLaterToolExecutions() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            toolCallResponse("call_2", "weather", "{\"location\":\"NYC\"}", 4, 5),
            chatResponse("Done", "stop", 6, 7)
        );
        var model = languageModel(chatModel, "openai");
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
    void generateTextAllowsMoreThanTheFormerTenStepCeiling() {
        var chatModel = mock(ChatModel.class);
        var providerCalls = new AtomicInteger();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            var call = providerCalls.getAndIncrement();
            if (call < 11) {
                return toolCallResponse("call_" + call, "research", "{}", 2, 3);
            }
            return chatResponse("Done", "stop", 4, 5);
        });
        var model = languageModel(chatModel, "openai");
        var toolExecutions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Research thoroughly")
            .tools(List.of(ToolDefinition.builder()
                .name("research")
                .executor(context -> {
                    toolExecutions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(12))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(12);
                assertThat(result.getText()).isEqualTo("Done");
                assertThat(result.getToolResults()).hasSize(11);
            })
            .verifyComplete();

        assertThat(toolExecutions).hasValue(11);
        verify(chatModel, times(12)).call(any(Prompt.class));
    }

    @Test
    void generateTextRunsLocalValidationWhenStrictOrExamplesAreUnsupportedByProvider() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3),
            chatResponse("Recovered after validation failed.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");
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
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getText()).isEqualTo("Recovered after validation failed.");
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
        var model = languageModel(chatModel, "openai");
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

    private List<Prompt> capturedCallPrompts(ChatModel chatModel) {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        return captor.getAllValues();
    }

    private List<ToolResponseMessage.ToolResponse> toolResponses(Prompt prompt) {
        return prompt.getInstructions().stream()
            .filter(ToolResponseMessage.class::isInstance)
            .map(ToolResponseMessage.class::cast)
            .flatMap(message -> message.getResponses().stream())
            .toList();
    }
}
