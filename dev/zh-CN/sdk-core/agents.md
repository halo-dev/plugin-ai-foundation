# Agent 运行时

简体中文 | [English](../../en/sdk-core/agents.md)

`run.halo.aifoundation.agent` 提供可复用、不可变且供应商中立的 Agent 定义。它在每次调用时
组合一个新的 `GenerateTextRequest`，然后委托给现有 `LanguageModel`。工具循环、结构化输出、
审批、外部工具、middleware、生命周期、取消和结果聚合仍由同一套模型运行时负责。

## 解析模型并创建定义

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

定义会防御性复制集合与 map，适合作为 Bean 复用。不要在调用之间修改工具、metadata 或
request builder；每次调用都会得到独立请求。没有显式 `stopWhen` 时，Agent 最多执行 20 个
模型步骤，并在没有可执行的继续动作时提前结束。直接调用 `LanguageModel` 的默认步骤行为不变。

调用准备的顺序固定为：定义默认值、调用输入与运行控制、定义与调用级 middleware / lifecycle、
一次异步 `prepareCall`、每个模型步骤的 `prepareStep`。`prepareCall` 可为当前调用替换模型或修改
策略，并异步返回 `PreparedAgentCall`；它不会修改可复用定义。

## 调用与结果

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

`generate` 与 `stream` 返回现有结果类型。流式结果的多个视图共享一次准备和一次 Provider 执行；
同时读取 `textStream()`、`fullStream()` 与 `result()` 不会重复工具或准备副作用。继续会话时保存
`responseMessages`，不能只保存最终文本。

结构化输出放在定义的 `output` 中。最终值来自 `GenerateTextResult.getOutput()` 或
`StreamTextResult.output()`；不要把助手文本再次解析成业务结果。

## 分步策略与生命周期

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

`prepareStep` 只影响当前模型步骤；调用准备只运行一次。用 `GenerationLifecycle` 观测请求、步骤、
工具、审批和终止事件。生命周期用于诊断，不代替最终结果；回调失败会成为 warning。

定义级 `activeTools` 未设置时，所有已定义工具都可用；显式传入空列表会禁用全部工具。
`prepareStep` 返回的非空 `activeTools` 优先于定义级设置，返回 `null` 表示该步骤不覆盖定义策略。

## 工具的完整生命周期

服务端工具包含 `executor`。没有 executor 的工具由调用方在外部完成。审批工具使用
`needsApproval(true)` 或动态 `approvalPredicate`：

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
    .build(); // 无 executor：外部工具
```

审批请求、外部结果或错误必须作为 UI / Model Message 历史的一部分继续提交，并保留原始
`toolCallId`。授权、幂等、审计和副作用确认属于消费插件的工具实现。

## 参数错误与工具更名恢复

`ToolCallRepairCallback` 处理两种明确失败：

- `INVALID_INPUT`：工具存在，但输入不符合 schema；`context.getTool()` 非空。
- `UNKNOWN_TOOL`：名称不在当前可用工具中；`context.getTool()` 为空，
  `context.getAvailableTools()` 给出可选目标。

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

恢复必须保留调用 ID。已知工具输入修复不能换名；未知工具只能映射到当前可用工具。运行时会重新
检查目标是否存在、输入 schema、审批与执行策略。回调缺失、抛错、改变 ID、指向不可用名称或仍然
产生无效输入时，不会执行工具，而是保留安全错误并产生稳定 warning。执行器错误、审批拒绝、
输出 schema 错误、取消和超时不进入恢复。

## Java UI Message endpoint 与浏览器续跑

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

端点负责从已认证请求派生类型化 call options；Transport 不能替换 Agent 的 instructions、tools、
output 或 stop policy。`streamAgent` 与直接模型入口共用消息校验、转换、regenerate、推理历史策略、
取消、聚合和 wire chunks。

浏览器继续使用现有 `Chat` / `useChat`、`DefaultChatTransport`、工具审批与
`addToolOutput` / `addToolApprovalResponse`。没有 Agent 专用 Transport。持久化最终归并后的
UI Message，恢复前用公开 schema 校验；自动续跑应使用有限步数与明确 predicate。

## 运行时边界

Agent 只负责一次调用的策略组合与模型执行。消费插件仍然拥有：

- 会话、UI Message、run、checkpoint 与业务状态的持久化；
- 中断后恢复、后台调度、重试队列、分布式协调和定时任务；
- 长期记忆、检索索引、租户与用户授权、配额、速率限制和审计；
- 浏览器、编辑器、内容发布、网络访问等业务工具及其副作用安全。

因此，进程重启后不能仅凭 `Agent` 对象恢复一次未完成运行。需要持久运行时的消费插件应保存消息、
工具状态和业务 checkpoint，再创建新的 Agent 调用继续。
