package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallFailureKind;
import run.halo.aifoundation.tool.ToolCallRepairContext;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolDefinition;

class LanguageModelToolRepairTest extends LanguageModelTestSupport {

    @Test
    void generateTextRecoversRenamedToolAndPreservesCallIdentity() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "legacyWeather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var executions = new AtomicInteger();
        var recoveryContext = new AtomicReference<ToolCallRepairContext>();
        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .context(Map.of("tenant", "demo"))
            .tools(List.of(repairableWeatherTool(context -> {
                executions.incrementAndGet();
                return Mono.just(Map.of("temperature", 22));
            })))
            .toolCallRepair(context -> {
                recoveryContext.set(context);
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .toolCallId(context.getToolCall().getToolCallId())
                    .toolName("weather")
                    .input(context.getToolCall().getInput())
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getToolCalls()).singleElement().satisfies(call -> {
                    assertThat(call.getToolCallId()).isEqualTo("call_1");
                    assertThat(call.getToolName()).isEqualTo("weather");
                });
                assertThat(result.getToolResults()).singleElement().satisfies(toolResult -> {
                    assertThat(toolResult.getToolCallId()).isEqualTo("call_1");
                    assertThat(toolResult.getToolName()).isEqualTo("weather");
                });
                assertThat(result.getWarnings()).anySatisfy(warning -> {
                    assertThat(warning.getCode()).isEqualTo("tool-call-repaired");
                    assertThat(warning.getProviderMetadata())
                        .containsEntry("originalToolName", "legacyWeather")
                        .containsEntry("resolvedToolName", "weather");
                });
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        assertThat(recoveryContext.get().getFailureKind())
            .isEqualTo(ToolCallFailureKind.UNKNOWN_TOOL);
        assertThat(recoveryContext.get().getTool()).isNull();
        assertThat(recoveryContext.get().getAvailableTools())
            .extracting(ToolDefinition::getName)
            .containsExactly("weather");
        assertThat(recoveryContext.get().getStepIndex()).isZero();
        assertThat(recoveryContext.get().getMessages()).isNotEmpty();
        assertThat(recoveryContext.get().getRequestContext()).containsEntry("tenant", "demo");

        var secondPrompt = capturedPrompts(chatModel).get(1);
        assertThat(secondPrompt.getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .flatMap(message -> message.getToolCalls().stream()))
            .singleElement()
            .satisfies(call -> {
                assertThat(call.id()).isEqualTo("call_1");
                assertThat(call.name()).isEqualTo("weather");
            });
    }

    @Test
    void unknownToolWithoutAvailableTargetDoesNotInvokeRecovery() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "missing", "{}", 2, 3),
            chatResponse("Recovered after error.", "stop", 4, 5)
        );
        var recoveries = new AtomicInteger();
        var request = GenerateTextRequest.builder()
            .prompt("Use a tool")
            .tools(List.of())
            .toolCallRepair(context -> {
                recoveries.incrementAndGet();
                return Mono.just(ToolCallRepairResult.unrepaired());
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolErrors()).singleElement()
                    .satisfies(error -> assertThat(error.getErrorText())
                        .isEqualTo("Unknown tool: missing"));
                assertThat(result.getWarnings()).extracting("code")
                    .doesNotContain("tool-call-repair-failed");
            })
            .verifyComplete();
        assertThat(recoveries).hasValue(0);
    }

    @Test
    void unknownToolRejectsEveryUnsafeRecoveryShape() {
        List<Function<ToolCallRepairContext, Mono<ToolCallRepairResult>>> recoveries = List.of(
            context -> Mono.just(ToolCallRepairResult.unrepaired()),
            context -> Mono.empty(),
            context -> Mono.error(new IllegalStateException("recovery failed")),
            context -> Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                .toolCallId("changed")
                .toolName("weather")
                .input(Map.of("location", "SF"))
                .build())),
            context -> Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                .toolCallId("call_1")
                .toolName("unavailable")
                .input(Map.of("location", "SF"))
                .build())),
            context -> Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                .toolCallId("call_1")
                .toolName("weather")
                .input(Map.of("city", "SF"))
                .build()))
        );

        recoveries.forEach(recovery -> {
            var chatModel = mock(ChatModel.class);
            when(chatModel.call(any(Prompt.class))).thenReturn(
                toolCallResponse("call_1", "legacyWeather", "{\"location\":\"SF\"}", 2, 3),
                chatResponse("Recovered after error.", "stop", 4, 5)
            );
            var executions = new AtomicInteger();
            var request = GenerateTextRequest.builder()
                .prompt("Weather")
                .tools(List.of(repairableWeatherTool(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of());
                })))
                .toolCallRepair(recovery::apply)
                .stopWhen(StopCondition.stepCountIs(2))
                .build();

            var result = languageModel(chatModel, "openai").generateText(request).block();
            assertThat(result.getToolErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorText())
                    .isEqualTo("Unknown tool: legacyWeather"));
            assertThat(result.getWarnings()).extracting("code")
                .contains("tool-call-repair-failed");
            assertThat(executions).hasValue(0);
        });
    }

    @Test
    void recoveredUnknownToolStillRequiresApprovalBeforeExecution() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "legacyWeather", "{\"location\":\"SF\"}", 2, 3)
        );
        var executions = new AtomicInteger();
        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .needsApproval(true)
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of());
                })
                .build()))
            .toolCallRepair(context -> Mono.just(ToolCallRepairResult.repaired(
                ToolCall.builder()
                    .toolCallId(context.getToolCall().getToolCallId())
                    .toolName("weather")
                    .input(context.getToolCall().getInput())
                    .build())))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolApprovalRequests()).singleElement()
                    .satisfies(approval -> {
                        assertThat(approval.getToolCallId()).isEqualTo("call_1");
                        assertThat(approval.getToolName()).isEqualTo("weather");
                    });
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired");
            })
            .verifyComplete();
        assertThat(executions).hasValue(0);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void recoveredUnknownExternalToolIsHandedOffWithoutExecution() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "legacyWeather", "{\"location\":\"SF\"}", 2, 3)
        );
        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .build()))
            .toolCallRepair(context -> Mono.just(ToolCallRepairResult.repaired(
                ToolCall.builder()
                    .toolCallId(context.getToolCall().getToolCallId())
                    .toolName("weather")
                    .input(context.getToolCall().getInput())
                    .build())))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolCalls()).singleElement().satisfies(call -> {
                    assertThat(call.getToolCallId()).isEqualTo("call_1");
                    assertThat(call.getToolName()).isEqualTo("weather");
                });
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired", "external-tool-pending");
            })
            .verifyComplete();
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void generateTextReportsMalformedJsonBeforeSchemaValidationAndPreservesPairing() {
        var chatModel = mock(ChatModel.class);
        var malformedArguments = """
            {"documentVersion":"v1","content":"<h2 id="what-is-ai-agent">Agent</h2>"}
            """.strip();
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "editorEdit", malformedArguments, 2, 3),
            chatResponse("The tool arguments were malformed.", "stop", 4, 5)
        );
        var executions = new AtomicInteger();
        var model = languageModel(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Edit document")
            .tools(List.of(ToolDefinition.builder()
                .name("editorEdit")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "documentVersion", Map.of("type", "string"),
                        "content", Map.of("type", "string")
                    ),
                    "required", List.of("documentVersion", "content")
                ))
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolErrors()).singleElement()
                    .satisfies(error -> assertThat(error.getErrorText())
                        .startsWith("Tool arguments contain malformed JSON at character")
                        .doesNotContain("documentVersion"));
                assertThat(result.getToolCalls()).singleElement()
                    .satisfies(toolCall -> {
                        assertThat(toolCall.getRawInput()).isEqualTo(malformedArguments);
                        assertThat(toolCall.getInput()).isEmpty();
                        assertThat(toolCall.getInputParseError()).isNotNull();
                        assertThat(toolCall.getInputParseError().getCharacterOffset()).isPositive();
                    });
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
        var prompts = capturedPrompts(chatModel);
        assertPairedToolError(prompts.get(1), "call_1",
            "Tool arguments contain malformed JSON");
    }

    @Test
    void generateTextRepairsMalformedJsonUsingRawInputAndRevalidatesSchema() {
        var chatModel = mock(ChatModel.class);
        var malformedArguments = "{\"location\":\"S\"F\"}";
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", malformedArguments, 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");
        var repairContext = new AtomicReference<ToolCallRepairContext>();
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(repairableWeatherTool(context -> {
                executions.incrementAndGet();
                return Mono.just(Map.of("temperature", 22));
            })))
            .toolCallRepair(context -> {
                repairContext.set(context);
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", "SF"))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired");
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        assertThat(repairContext.get().getValidationError())
            .startsWith("Tool arguments contain malformed JSON at character");
        assertThat(repairContext.get().getToolCall().getRawInput())
            .isEqualTo(malformedArguments);
        assertThat(repairContext.get().getToolCall().getInputParseError()).isNotNull();
    }

    @Test
    void generateTextRepairsInvalidToolInputExecutesAndContinues() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");
        var repairContext = new AtomicReference<ToolCallRepairContext>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .context(Map.of("tenant", "demo"))
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22)))))
            .toolCallRepair(context -> {
                repairContext.set(context);
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired");
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getSteps().getFirst().getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .map(part -> part.getType()))
                    .containsExactly(PartType.TOOL_CALL, PartType.TOOL_RESULT);
            })
            .verifyComplete();

        assertThat(repairContext.get().getRequestContext()).containsEntry("tenant", "demo");
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .flatMap(message -> message.getToolCalls().stream())
            .map(AssistantMessage.ToolCall::arguments))
            .anySatisfy(arguments -> assertThat(arguments).contains("\"location\":\"SF\""));
    }

    @Test
    void generateTextRepairAllowsNullValuesInRequestContext() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = languageModel(chatModel, "openai");
        var repairs = new AtomicInteger();
        var requestContext = new LinkedHashMap<String, Object>();
        requestContext.put("tenant", null);

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .context(requestContext)
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22)))))
            .toolCallRepair(context -> {
                repairs.incrementAndGet();
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired")
                    .doesNotContain("tool-call-repair-failed");
            })
            .verifyComplete();

        assertThat(repairs).hasValue(1);
    }

    private List<Prompt> capturedPrompts(ChatModel chatModel) {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        return captor.getAllValues();
    }

    private void assertPairedToolError(Prompt prompt, String toolCallId,
        String expectedErrorText) {
        assertThat(prompt.getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .flatMap(message -> message.getToolCalls().stream())
            .map(AssistantMessage.ToolCall::id))
            .contains(toolCallId);
        assertThat(prompt.getInstructions().stream()
            .filter(ToolResponseMessage.class::isInstance)
            .map(ToolResponseMessage.class::cast)
            .flatMap(message -> message.getResponses().stream())
            .filter(response -> toolCallId.equals(response.id())))
            .singleElement()
            .satisfies(response -> assertThat(response.responseData())
                .contains(expectedErrorText));
    }
}
