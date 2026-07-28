# SDK Core: Tools and multi-step generation

[简体中文](../../zh-CN/sdk-core/tools-and-tool-calling.md) | English

## Server-side tools

```java
ToolDefinition weather = ToolDefinition.builder()
    .name("weather")
    .description("Get the current weather for a city.")
    .inputSchema(JsonSchema.object()
        .property("city", JsonSchema.string())
        .required("city"))
    .strict(true)
    .executor(context -> weatherService.current(
        String.valueOf(context.getInput().get("city"))))
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("What is the weather in Hangzhou?")
    .tools(List.of(weather))
    .toolChoice(ToolChoice.auto())
    .stopWhen(StopCondition.stepCountIs(4))
    .build();
```

Without `stopWhen`, generation performs one model step. Add an explicit budget for an autonomous
tool loop.

## Step control and external tools

`prepareStep` can change active tools, messages, tool choice, output, sampling, retries, or the next
stop condition for one step:

```java
.prepareStep(context -> context.getStepIndex() == 0
    ? PreparedStep.builder().activeTools(List.of("search")).build()
    : PreparedStep.builder().activeTools(List.of("fetch")).build())
```

A tool without an executor is external. Persist the assistant `responseMessages`, execute the tool
in the consumer application, add one result or error for the same `toolCallId`, then send the
updated history in a new request.

## Approval and repair

Configure `approvalPolicy` or `approvalPredicate` on `ToolDefinition`. An approval request ends the
current call with durable response messages. Record a `ToolApprovalResponse`, then continue with a
second request. A denial is a decision, not a tool execution failure.

`ToolCallRepairCallback` may repair invalid input for a known server-side tool before execution.
Return `ToolCallRepairResult` with a corrected `ToolCall`; keep the original call ID and tool name.

Tool input callbacks observe input start, delta, and availability. Providers may emit real deltas
or only a final `input-available` event, so both paths are valid.

Final results expose `toolCalls`, `toolResults`, `toolErrors`, steps, warnings, and authoritative
`responseMessages`. Each assistant tool call that is continued must be paired with exactly one
result, error, or approval outcome.
