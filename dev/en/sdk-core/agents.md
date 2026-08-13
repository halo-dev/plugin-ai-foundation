# Agent runtime

[简体中文](../../zh-CN/sdk-core/agents.md) | English

`run.halo.aifoundation.agent` provides reusable, immutable, provider-neutral agent definitions.
Each invocation composes a fresh `GenerateTextRequest` and delegates it to the existing
`LanguageModel`. The same model runtime remains authoritative for tool loops, structured output,
approval, external tools, middleware, lifecycle, cancellation, and result aggregation.

## Resolve a model and create a definition

```java
record AnswerOptions(String style) {
}

Mono<Agent<AnswerOptions>> createAgent(AiModelService service, String modelName) {
    return service.languageModel(modelName)
        .map(model -> Agent.create(AgentOptions.forModel(model, AnswerOptions.class)
            .id("site-assistant")
            .instructions("Answer questions about this site.")
            .maxOutputTokens(1024)
            .tools(List.of(searchTool(), handoffTool()))
            .callValidator(options -> {
                if (options == null || options.style() == null) {
                    throw new IllegalArgumentException("style is required");
                }
            })
            .prepareCall(context -> {
                var suffix = switch (context.getOptions().style()) {
                    case "brief" -> " Keep the final answer brief.";
                    case "detailed" -> " Explain the answer in detail.";
                    default -> throw new IllegalArgumentException("unsupported style");
                };
                context.getRequestBuilder().system(
                    "Answer questions about this site." + suffix);
                return Mono.just(context.prepared());
            })
            .build()));
}
```

Definitions defensively copy collections and maps and can be reused as beans. Do not mutate tools,
metadata, or request builders between calls. An agent without an explicit `stopWhen` permits at
most 20 model steps and still ends early when no executable continuation exists. Direct
`LanguageModel` defaults are unchanged.

Composition order is definition defaults, call input and operational controls, definition and call
middleware/lifecycle, one asynchronous `prepareCall`, and then `prepareStep` for each model step.
Call preparation returns `PreparedAgentCall` and may replace the model or change policy for that
invocation only.

## Calls and results

```java
AgentCall<AnswerOptions> call = AgentCall.<AnswerOptions>builder()
    .prompt("Summarize the latest article")
    .options(new AnswerOptions("brief"))
    .metadata(Map.of("operation", "summary"))
    .context(Map.of("postName", "hello-halo"))
    .cancellationToken(cancellation.token())
    .build();

Mono<GenerateTextResult> generated = agent.generate(call);
StreamTextResult streamed = agent.stream(call);

Flux<String> text = streamed.textStream();
Mono<GenerateTextResult> finalResult = streamed.result();
Mono<List<ModelMessage>> responseMessages = streamed.responseMessages();
```

`generate` and `stream` return the existing result types. All projections of one streaming result
share one preparation and one provider execution. Reading `textStream()`, `fullStream()`, and
`result()` together does not repeat tools or preparation side effects. Preserve `responseMessages`
when continuing a conversation.

Put structured output policy on the definition. Read the validated value from
`GenerateTextResult.getOutput()` or `StreamTextResult.output()` rather than reparsing assistant
text.

## Step policy and lifecycle

```java
Agent<AnswerOptions> agent = Agent.create(AgentOptions
    .forModel(model, AnswerOptions.class)
    .instructions("Plan, then answer.")
    .tools(List.of(planTool, answerTool))
    .prepareStep(context -> context.getStepIndex() == 0
        ? PreparedStep.builder().activeTools(List.of("plan")).build()
        : PreparedStep.builder().activeTools(List.of("answer")).build())
    .stopWhen(StopCondition.stepCountIs(6))
    .lifecycle(List.of(lifecycle))
    .build());
```

`prepareStep` affects one model step; call preparation runs once. Observe request, step, tool,
approval, and terminal events with `GenerationLifecycle`. Lifecycle callbacks are diagnostics, not
business results, and callback failures become warnings.

When definition-level `activeTools` is unset, every defined tool remains available; an explicit
empty list disables all tools. A non-null `activeTools` returned by `prepareStep` takes precedence,
while returning `null` leaves the definition policy unchanged for that step.

## Complete tool lifecycle

A server tool has an `executor`. A tool without an executor is completed by the caller. Approval
uses `needsApproval(true)` or a dynamic `approvalPredicate`:

```java
ToolDefinition publish = ToolDefinition.builder()
    .name("publish")
    .description("Publish a reviewed draft")
    .inputSchema(Map.of(
        "type", "object",
        "properties", Map.of("postName", Map.of("type", "string")),
        "required", List.of("postName")
    ))
    .needsApproval(true)
    .executor(context -> publishingService.publish(
        String.valueOf(context.getInput().get("postName"))))
    .build();

ToolDefinition browserLookup = ToolDefinition.builder()
    .name("browser_lookup")
    .description("Look up data in the caller's browser")
    .inputSchema(Map.of("type", "object"))
    .build(); // no executor: external tool
```

Submit approval decisions and external results or errors as continued UI or model-message history,
preserving the original `toolCallId`. Authorization, idempotency, audit, and side-effect safety
belong to the consumer's tool implementation.

## Invalid input and renamed-tool recovery

`ToolCallRepairCallback` receives two explicit failure kinds:

- `INVALID_INPUT`: the tool exists but its input fails schema validation; `getTool()` is present.
- `UNKNOWN_TOOL`: the name is absent from the current tool set; `getTool()` is null and
  `getAvailableTools()` lists valid targets.

```java
.toolCallRepair(context -> {
    var original = context.getToolCall();
    if (context.getFailureKind() == ToolCallFailureKind.UNKNOWN_TOOL
        && original.getToolName().equals("legacy_search")) {
        return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
            .toolCallId(original.getToolCallId())
            .toolName("search")
            .input(original.getInput())
            .build()));
    }
    if (context.getFailureKind() == ToolCallFailureKind.INVALID_INPUT
        && original.getToolName().equals("search")) {
        return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
            .toolCallId(original.getToolCallId())
            .toolName(original.getToolName())
            .input(Map.of("query", String.valueOf(original.getInput().get("q"))))
            .build()));
    }
    return Mono.just(ToolCallRepairResult.unrepaired());
})
```

Recovery must retain the call id. Known-tool input repair cannot rename the tool; unknown-tool
recovery can select only a currently available tool. The runtime revalidates existence, input
schema, approval, and execution policy. A missing or throwing callback, changed id, unavailable
name, or still-invalid input never executes and produces the safe original error plus a stable
warning. Executor failures, denied approval, output-schema failures, cancellation, and timeouts are
not recovery inputs.

## Java UI Message endpoint and browser continuation

```java
UIMessageChatResult<MyMetadata> chat = UIMessageChatHandlers.streamAgent(
    agent,
    chatRequest,
    new AnswerOptions("brief"),
    options -> options
        .metadataSupplier(MyMetadata::empty)
        .serializer(json::writeValueAsString)
        .cancellationToken(cancellation.token())
        .onFinish(finish -> conversations.save(finish.messages()))
);

UIMessageStreamResponse response = chat.response();
```

The authenticated endpoint derives typed call options. Transport input cannot replace agent
instructions, tools, output, or stop policy. `streamAgent` shares validation, conversion,
regeneration, reasoning history, cancellation, aggregation, and wire chunks with direct-model
execution.

The browser continues to use the existing `Chat` or `useChat`, `DefaultChatTransport`, approval,
`addToolOutput`, and `addToolApprovalResponse` APIs. There is no agent-specific transport. Persist
final reduced UI messages, validate restored data with public schemas, and bound automatic
continuation with an explicit predicate and step limit.

## Runtime boundary

The agent owns policy composition and one invocation. The consumer still owns:

- durable conversations, UI messages, runs, checkpoints, and business state;
- resume after restart, background scheduling, retry queues, and distributed coordination;
- long-term memory, retrieval indexes, authorization, quotas, rate limits, and audit;
- browser, editor, publishing, network, and other business tools and their side effects.

An in-memory `Agent` cannot resume an unfinished run after a process restart. A durable consumer
must store messages, tool state, and business checkpoints, then start a new agent invocation to
continue.
