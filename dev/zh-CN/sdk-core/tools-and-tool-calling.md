# SDK Core：工具调用与多步骤

简体中文 | [English](../../en/sdk-core/tools-and-tool-calling.md)

工具由 `ToolDefinition` 声明，并在每个 `GenerateTextRequest` 中显式提供。模型只会看到本次
请求的工具，不存在全局自动注册的工具目录。

## 服务端工具

```java
ToolDefinition weather = ToolDefinition.builder()
    .name("get_weather")
    .description("查询指定城市的当前天气")
    .inputSchema(JsonSchema.object()
        .property("city", JsonSchema.string().description("城市名称"))
        .required("city")
        .build())
    .outputSchema(JsonSchema.object()
        .property("city", JsonSchema.string())
        .property("temperature", JsonSchema.number())
        .required("city", "temperature")
        .build())
    .executor(context -> {
        String city = (String) context.getInput().get("city");
        return weatherClient.current(city)
            .map(value -> (Object) Map.of(
                "city", city,
                "temperature", value.temperature()));
    })
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("杭州今天适合出门吗？")
    .tools(List.of(weather))
    .toolChoice(ToolChoice.auto())
    .stopWhen(StopCondition.toolCalls(3))
    .build();

return model.generateText(request);
```

`executor` 返回 `Mono<Object>` 语义的响应式结果，结果应可序列化为 JSON。`outputSchema`
用于描述和校验约定，不会把任意 Java 对象自动转换为特定 DTO。

## Tool choice

| API                       | 行为                                               |
| ------------------------- | -------------------------------------------------- |
| `ToolChoice.auto()`       | 由模型决定是否调用工具                             |
| `ToolChoice.required()`   | 要求模型至少调用一个可用工具，前提是 Provider 支持 |
| `ToolChoice.tool("name")` | 指定工具，具体强制程度取决于 Provider              |
| `ToolChoice.none()`       | 本次请求禁用工具                                   |

省略 `toolChoice` 时使用 Provider 默认行为，通常等价于 auto，但调用方不应依赖某个供应方的
隐式默认值。

## 单步与多步骤

没有 `stopWhen` 时只执行一个模型步骤。即使该步骤执行了服务端工具，也不会自动再调用一次模型。

```java
.stopWhen(StopCondition.stepCountIs(4))
```

`stepCountIs(4)` 最多允许四个模型步骤。

如果只希望在本步骤包含成功工具结果时继续：

```java
.stopWhen(StopCondition.toolCalls(4))
```

服务端还会使用 `halo.ai-foundation.language.max-steps` 施加绝对上限。调用方仍应通过
`stopWhen` 设置更小的业务预算，避免无边界循环。

最终结果包含：

```java
result.getSteps();
result.getToolCalls();
result.getToolResults();
result.getToolErrors();
result.getResponseMessages();
```

## 按步骤切换工具和参数

`prepareStep` 在每个模型步骤开始前运行，只覆盖非 null 字段：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("先检索资料，再给出答案")
    .tools(List.of(searchTool, answerTool))
    .prepareStep(context -> context.getStepIndex() == 0
        ? PreparedStep.builder()
            .activeTools(List.of("search"))
            .toolChoice(ToolChoice.tool("search"))
            .build()
        : PreparedStep.builder()
            .activeTools(List.of("answer"))
            .temperature(0.2)
            .build())
    .stopWhen(StopCondition.stepCountIs(2))
    .build();
```

`activeTools` 必须引用请求中已经声明的工具。它用于每一步的可用性过滤，不是动态注册未知工具。

## 外部工具

省略 `executor` 即表示由调用方或前端执行：

```java
ToolDefinition openBrowser = ToolDefinition.builder()
    .name("open_browser")
    .description("打开指定 URL")
    .inputSchema(JsonSchema.object()
        .property("url", JsonSchema.string())
        .required("url")
        .build())
    .build();
```

第一次生成返回 assistant tool call。调用方执行工具后，把工具结果加入消息历史，再发起下一次
生成。若使用 UI Message，不应创建 UI 的 `tool` role 消息；工具状态保存在 assistant 消息的
动态 `tool-*` part 中，详见
[SDK UI：工具交互](../sdk-ui/chatbot-tool-usage.md)。

## 工具审批

始终审批：

```java
ToolDefinition deletePost = ToolDefinition.builder()
    .name("delete_post")
    .description("删除文章")
    .inputSchema(deletePostSchema)
    .needsApproval(true)
    .executor(this::deletePost)
    .build();
```

动态审批：

```java
.needsApproval(context -> {
    Object permanent = context.getInput().get("permanent");
    return Boolean.TRUE.equals(permanent);
})
```

审批发生在输入解析与 schema 校验之后、executor 之前。审批不会挂起当前 HTTP 请求；本次结果会
包含 `tool-approval-request`，调用方加入审批响应后再发起下一次生成。

审批拒绝不是工具执行异常。UI 状态应使用 `approval-responded`，必要时由后端返回
`output-denied`，不要伪装成 `output-error`。

## 流式工具输入

```java
ToolDefinition weather = ToolDefinition.builder()
    .name("get_weather")
    .inputSchema(weatherSchema)
    .onInputStart(context ->
        audit("start", context.getToolCallId()))
    .onInputDelta(context ->
        audit("delta", context.getInputTextDelta()))
    .onInputAvailable(context ->
        audit("available", context.getInput()))
    .executor(context -> executeWeather(context.getInput()))
    .build();
```

当 Provider 真实提供参数增量时，顺序为：

```text
onInputStart
  -> tool-input-start
  -> onInputDelta / tool-input-delta (0..n)
  -> tool-input-end
  -> tool-call
  -> onInputAvailable
  -> approval / external handoff / executor
```

并非所有 Provider 都有 delta。通用 Spring AI `ChatModel` 与 Ollama 是 final-only；
OpenAI-compatible 也只有在实际响应带可追加参数片段时才发 delta。调用方必须同时接受
“start + delta + available”和“直接 available”。

## 修复无效工具输入

已知工具的输入不符合 schema 时，可提供一次修复机会：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("查询杭州天气")
    .tools(List.of(weather))
    .toolCallRepair(context -> {
        ToolCall original = context.getToolCall();
        Map<String, Object> repairedInput = Map.of(
            "city", String.valueOf(original.getInput().get("location")));

        return Mono.just(ToolCallRepairResult.repaired(
            ToolCall.builder()
                .toolCallId(original.getToolCallId())
                .toolName(original.getToolName())
                .input(repairedInput)
                .rawInput(original.getRawInput())
                .providerMetadata(original.getProviderMetadata())
                .build()));
    })
    .build();
```

修复回调最多执行一次，修复后的输入会再次进行完整校验。返回
`ToolCallRepairResult.unrepaired()` 会保留原校验失败。

修复只适用于请求中已经声明的工具，不会把模型捏造的工具名变成可调用工具。

## 错误语义

- 输入解析、schema 校验或修复失败会产生该调用自己的 `ToolError`。
- 一个工具失败不会自动抹掉同一步中的其他工具结果。
- executor 错误可以进入消息历史，使后续步骤有机会修正策略。
- Provider 从未给出工具名等协议错误会终止当前步骤。
- 回调失败会终止生成；普通 executor 失败按工具错误处理。

要让模型看到工具结果或错误并继续，必须同时提供多步骤 `stopWhen`，或在外部执行后发起下一次
请求。
