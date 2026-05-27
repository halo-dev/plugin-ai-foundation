# AI 基础设施插件 — 开发者集成指南

本文档面向需要在 Halo 插件中调用 AI 能力的开发者。

## 前置条件

- 本插件（ai-foundation）已安装并启用
- 已至少配置一个 AI 提供商和模型（通过 Halo 控制台 > AI 基础设施）
- 调用方插件已在 `plugin.yaml` 中声明对 `ai-foundation` 的插件依赖

## 添加依赖

在你的插件 `build.gradle` 中添加对 `api` 模块的 `compileOnly` 依赖：

```gradle
repositories {
    mavenLocal()
}

dependencies {
    compileOnly platform('run.halo.tools.platform:plugin:2.23.0')
    compileOnly 'run.halo.app:api'
    compileOnly 'run.halo.aifoundation:api:1.0.0-SNAPSHOT'
}
```

如果调用方插件项目已经配置了 Halo 插件平台和 `run.halo.app:api` 依赖，只需要额外补充 `run.halo.aifoundation:api`。

然后在调用方插件的 `plugin.yaml` 中声明运行时插件依赖：

```yaml
spec:
  pluginDependencies:
    ai-foundation: ">=1.0.0-SNAPSHOT"
```

开发环境中可以先在本项目根目录执行 `./gradlew :api:publishToMavenLocal`，将 SDK 发布到本地 Maven 仓库。

## 核心接口

### AiModelService

`AiModelService` 是入口服务，同时也是 Halo 服务端 Extension Point。由于 Halo 插件间的 Spring ApplicationContext 隔离，调用方插件不要直接通过 `@Autowired` 注入 `AiModelService`，而是注入 Halo 提供的 `ExtensionGetter` 并获取启用的服务实现：

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@RequiredArgsConstructor
public class MyAiService {

    private final ExtensionGetter extensionGetter;

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class);
    }
}
```

它提供以下方法：

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `languageModel(String modelName)` | `Mono<LanguageModel>` | 获取指定语言模型实例（需在响应式上下文中订阅） |
| `embeddingModel(String modelName)` | `Mono<EmbeddingModel>` | 获取指定嵌入模型实例（需在响应式上下文中订阅） |
| `defaultLanguageModel()` | `Mono<LanguageModel>` | 获取默认语言模型实例 |
| `defaultEmbeddingModel()` | `Mono<EmbeddingModel>` | 获取默认嵌入模型实例 |
| `listModels()` | `Mono<List<ModelInfo>>` | 列出所有已配置的模型 |
| `listProviders()` | `Mono<List<ProviderInfo>>` | 列出所有已配置的提供商 |

### modelName 的格式

`modelName` 是 **AiModel 的 metadata.name**（即 Halo Extension 资源的名称），例如 `openai-prod-gpt-4o-a7f3k`。它不是提供商侧的 `modelId`，也不是 `providerName/modelId` 这样的组合字符串。

你可以通过 `listModels()` 获取所有可用模型的 `name` 字段。

## 语言模型调用

以下示例中的 `aiModelService` 均来自上文通过 `ExtensionGetter` 获取到的 `AiModelService` 实例。

### 简单文本生成

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

model.generateText("你好，请介绍一下 Halo CMS")
    .subscribe(result -> {
        System.out.println(result.getText());
        System.out.println("Finish reason: " + result.getFinishReason());
    });
```

### 结构化文本生成

`GenerateTextRequest` 支持两种输入方式：

- `prompt`：单轮用户提示词
- `messages`：多轮消息历史

`prompt` 和 `messages` 必须二选一，不能同时传入。系统提示词建议使用顶层 `system` 字段。

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是一个 helpful 的助手")
    .messages(List.of(
        ModelMessage.user("你好"),
        ModelMessage.assistant("你好！有什么可以帮你？"),
        ModelMessage.user("请介绍一下 Halo CMS")
    ))
    .temperature(0.7)
    .maxOutputTokens(2048)
    .topP(0.9)
    .providerOptions(Map.of(
        "openai", Map.of("seed", 42)
    ))
    .build();

model.generateText(request)
    .subscribe(result -> {
        System.out.println(result.getText());
        System.out.println("Steps: " + result.getSteps().size());
        if (result.getUsage() != null) {
            System.out.println("Input tokens: " + result.getUsage().getInputTokens());
            System.out.println("Output tokens: " + result.getUsage().getOutputTokens());
            System.out.println("Total tokens: " + result.getUsage().getTotalTokens());
        }
        if (result.getResponse() != null) {
            System.out.println("Response model: " + result.getResponse().getModel());
        }
    });
```

### 结构化输出

如果调用方需要稳定的 JSON 对象、数组或枚举值，可以在 `GenerateTextRequest.output` 中声明 `OutputSpec`。模型仍会返回统一的 `GenerateTextResult`，其中 `text` 保留原始文本，`output` 是解析并校验后的结构化值，`outputText` 是用于解析的原始片段。

```java
Map<String, Object> schema = Map.of(
    "type", "object",
    "properties", Map.of(
        "title", Map.of("type", "string"),
        "summary", Map.of("type", "string"),
        "tags", Map.of(
            "type", "array",
            "items", Map.of("type", "string")
        )
    ),
    "required", List.of("title", "summary")
);

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("请总结 Halo CMS，并给出 3 个标签")
    .output(OutputSpec.object(schema))
    .build();

model.generateText(request)
    .subscribe(result -> {
        Map<?, ?> output = (Map<?, ?>) result.getOutput();
        System.out.println(output.get("title"));
        System.out.println(result.getOutputText());
    });
```

对于 Java record 或简单 Java 类，可以直接由类型生成基础 JSON Schema：

```java
record Summary(String title, String summary, List<String> tags) {
}

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("请总结 Halo CMS，并给出 3 个标签")
    .output(OutputSpec.object(Summary.class))
    .build();
```

`OutputSpec.array(Class<?>)` 会把类型作为数组元素 schema；`OutputSpec.choice(List.of("yes", "no"))` 会约束模型只能返回指定字符串；`OutputSpec.json()` 只要求返回合法 JSON，不校验具体结构。

流式调用时，结构化内容仍然通过 `text-delta` 输出，完整协议事件可从 `fullStream()` 读取。`streamText()` 返回的 `StreamTextResult` 还会基于同一次模型调用提供结构化视图：`partialOutputStream()` 会在对象或通用 JSON 文本可安全解析时发出快照，`elementStream()` 会在数组元素完整且通过元素 schema 校验后发出元素，`output()` 会在结束后返回最终解析并校验的结构化结果。最终校验仍然是权威结果；部分快照只适合用于渐进式 UI。

```java
StreamTextResult stream = model.streamText(request);

stream.fullStream()
    .subscribe(part -> {
        switch (part.getType()) {
            case TextStreamPart.TYPE_TEXT_DELTA -> System.out.print(part.getDelta());
            case TextStreamPart.TYPE_ERROR -> System.err.println(part.getErrorText());
            default -> {
            }
        }
    });

stream.output()
    .cast(Map.class)
    .subscribe(output -> System.out.println(output.get("title")));
```

如果 `output` 是对象或通用 JSON，可以订阅部分输出快照：

```java
model.streamText(request)
    .partialOutputStream()
    .subscribe(snapshot -> System.out.println("partial = " + snapshot));
```

如果 `output` 是数组，可以订阅已经完成并通过元素 schema 校验的元素：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("生成 3 个文章标题，返回 JSON 数组")
    .output(OutputSpec.array(Map.of(
        "type", "object",
        "properties", Map.of("title", Map.of("type", "string")),
        "required", List.of("title")
    )))
    .build();

model.streamText(request)
    .elementStream()
    .subscribe(element -> System.out.println("element = " + element));
```

本地校验当前覆盖常用 JSON Schema 子集：`type`、`enum`、`required`、`properties` 和 `items`。更复杂的格式约束可以在调用方拿到 `output` 后继续校验。

### 流式文本生成（推荐用于聊天场景）

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是一个 helpful 的助手")
    .messages(List.of(
        ModelMessage.user("你好")
    ))
    .temperature(0.7)
    .maxOutputTokens(2048)
    .build();

model.streamText(request).fullStream()
    .subscribe(part -> {
        switch (part.getType()) {
            case TextStreamPart.TYPE_TEXT_DELTA -> System.out.print(part.getDelta());
            case TextStreamPart.TYPE_REASONING_DELTA -> {
                // 推理内容与最终回答分离，是否展示或持久化由调用方决定。
                System.out.println("[Reasoning] " + part.getDelta());
            }
            case TextStreamPart.TYPE_FINISH_STEP -> {
                if (part.getUsage() != null) {
                    System.out.println("\n[Step tokens] " + part.getUsage().getTotalTokens());
                }
            }
            case TextStreamPart.TYPE_FINISH -> {
                System.out.println("\n[生成结束] " + part.getFinishReason());
                if (part.getUsage() != null) {
                    System.out.println("Input tokens: " + part.getUsage().getInputTokens());
                    System.out.println("Output tokens: " + part.getUsage().getOutputTokens());
                }
            }
            case TextStreamPart.TYPE_ERROR -> System.err.println("Error: " + part.getErrorText());
            default -> {
                // start、start-step、text-start、text-end、raw 等协议事件通常无需处理
            }
        }
    });
```

### 服务端工具调用与多步骤生成

工具调用采用请求级定义，调用方负责提供工具名称、描述、输入 JSON Schema 和服务端 executor。默认只执行一个模型步骤。需要自动执行工具并把结果继续交给模型时，显式设置 `stopWhen`，例如 `StopCondition.stepCountIs(2)`。

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("请查询旧金山天气，并用中文简短回答")
    .tools(List.of(ToolDefinition.builder()
        .name("weather")
        .description("查询指定城市当前天气")
        .inputSchema(Map.of(
            "type", "object",
            "properties", Map.of(
                "location", Map.of("type", "string", "description", "城市名称")
            ),
            "required", List.of("location")
        ))
        .outputSchema(Map.of(
            "type", "object",
            "properties", Map.of(
                "location", Map.of("type", "string"),
                "temperature", Map.of("type", "integer"),
                "condition", Map.of("type", "string")
            ),
            "required", List.of("location", "temperature", "condition")
        ))
        .executor(context -> Mono.just(Map.of(
            "location", context.getInput().get("location"),
            "temperature", 22,
            "condition", "sunny"
        )))
    .build()))
    .toolChoice(ToolChoice.auto())
    .stopWhen(StopCondition.stepCountIs(2))
    .build();

model.generateText(request)
    .subscribe(result -> {
        System.out.println(result.getText());
        for (GenerationStep step : result.getSteps()) {
            step.getToolCalls().forEach(call ->
                System.out.println("[Tool call] " + call.getToolName() + " " + call.getInput())
            );
            step.getToolResults().forEach(toolResult ->
                System.out.println("[Tool result] " + toolResult.getToolName() + " " + toolResult.getResult())
            );
            step.getToolErrors().forEach(error ->
                System.err.println("[Tool error] " + error.getToolName() + " " + error.getErrorText())
            );
        }
    });
```

`inputSchema` 用于约束模型生成的工具入参，`outputSchema` 用于约束 executor 返回值。`ToolDefinition.executor` 是服务端运行逻辑，属于调用方进程内代码，不会序列化到 HTTP/OpenAPI schema 中。executor 会收到 `ToolExecutionContext`，其中包含 `toolCallId`、`toolName`、解析后的 `input`、`stepIndex`、当前步骤消息和 provider metadata，方便调用方关联流式事件、审计日志或嵌套模型调用。若模型请求了未定义工具，会记录 `ToolError` 并停止后续步骤；若工具没有 executor，会记录 `tool-not-executed` warning 并停止后续步骤；若达到步骤限制仍有工具调用，会记录 `stop-condition-reached` warning。

工具调用同样支持 `streamText()`。与 `generateText()` 的一次性聚合不同，`streamText()` 会在每个模型步骤中立即转发 provider 返回的 `reasoning-delta` 和 `text-delta`；当模型步骤以工具调用结束时，再发送 `tool-call`，执行服务端工具并发送 `tool-result` 或 `tool-error`。如果 `stopWhen` 允许继续，下一次模型调用会作为新的 `start-step` 继续流式输出最终回答。

因此，工具调用不会让整个流退化为非流式。工具执行期间可能会短暂停顿，但调用方可以通过已经收到的 `tool-call` 事件展示“正在执行工具”的状态。

### 停止与取消生成

SDK 调用方有三类“停止”方式，作用层级不同：

1. `stopWhen`：控制多步骤生成是否继续进入下一次模型调用，主要用于工具循环。
2. `stopSequences`：传给模型提供商，要求模型在生成到指定文本序列时停止当前回答。
3. Reactor 取消订阅：取消当前 HTTP/流式请求，适合用户点击“停止生成”。

`stopWhen` 不会中断正在进行的模型调用，它只在一个步骤结束后决定是否开启下一步。默认不设置时只执行一个模型步骤。需要限制工具循环步数时：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("请查询天气并回答")
    .tools(List.of(weatherTool))
    .stopWhen(StopCondition.stepCountIs(2))
    .build();
```

如果希望只有在工具调用成功时才继续，可以使用：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("必要时调用工具")
    .tools(List.of(weatherTool))
    .stopWhen(StopCondition.toolCalls(3))
    .build();
```

也可以自定义条件：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("必要时调用工具")
    .tools(List.of(weatherTool))
    .stopWhen(context -> context.getStepIndex() < 2
        && context.getStep() != null
        && !context.getStep().getToolCalls().isEmpty()
        && context.getStep().getToolErrors().isEmpty())
    .build();
```

如果要取消正在进行的流式生成，保留订阅并调用 `dispose()`：

```java
Disposable disposable = model.streamText(request)
    .fullStream()
    .subscribe(part -> {
        // 渲染 text-delta、reasoning-delta、tool-call 等事件
    });

// 用户点击停止时
disposable.dispose();
```

如果调用方使用 WebFlux 返回 SSE，可以直接返回 `Flux`；客户端断开连接时，订阅会被取消：

```java
return model.streamText(request).fullStream();
```

对于非流式 `generateText()`，同样可以取消订阅，但调用方通常只能在 Reactor 链路层取消等待；具体提供商是否立即中止远端请求取决于底层 HTTP 客户端和 provider 实现：

```java
Disposable disposable = model.generateText(request)
    .subscribe(result -> {
        // 处理最终结果
    });

disposable.dispose();
```

### GenerateTextRequest 参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `system` | `String` | 系统提示词，建议优先使用此字段表达系统指令 |
| `prompt` | `String` | 单轮用户提示词，与 `messages` 二选一 |
| `messages` | `List<ModelMessage>` | 多轮消息列表，与 `prompt` 二选一 |
| `temperature` | `Double` | 采样温度（0~2） |
| `maxOutputTokens` | `Integer` | 最大生成 token 数 |
| `topP` | `Double` | 核采样参数 |
| `topK` | `Integer` | Top-K 采样参数 |
| `presencePenalty` | `Double` | Presence penalty |
| `frequencyPenalty` | `Double` | Frequency penalty |
| `stopSequences` | `List<String>` | 停止序列 |
| `tools` | `List<ToolDefinition>` | 请求级工具定义，包含名称、描述、输入/输出 JSON Schema 和可选 executor |
| `toolChoice` | `ToolChoice` | 工具选择策略：`AUTO`、`NONE`、`REQUIRED` 或指定 `TOOL` |
| `stopWhen` | `StopCondition` | Java 调用方的步骤继续条件；未设置时只执行一个模型步骤。该字段不会进入 OpenAPI/HTTP schema |
| `output` | `OutputSpec` | 结构化输出声明，支持 `TEXT`、`OBJECT`、`ARRAY`、`CHOICE`、`JSON` |
| `providerOptions` | `Map<String, Map<String, Object>>` | 按提供商命名空间分组的特定选项 |

`providerOptions` 必须按提供商分组，避免不同服务商的私有参数冲突，例如：

```java
Map.of(
    "openai", Map.of("seed", 42),
    "ollama", Map.of("num_ctx", 4096)
)
```

### ModelMessage 构造

消息内容采用类似 AI SDK `ModelMessage` 的 parts 结构。简单文本消息可以直接使用工厂方法：

```java
ModelMessage.user("用户消息");
ModelMessage.assistant("助手消息");
ModelMessage.system("系统消息");
```

如果需要显式构造文本 part：

```java
ModelMessage.builder()
    .role(ModelMessageRole.USER)
    .content(List.of(ModelMessagePart.text("用户消息")))
    .build();
```

`ASSISTANT` 角色消息还可以携带 `reasoning` part，用于恢复推理模型返回的推理内容。对于 DeepSeek 等要求后续请求回传 `reasoning_content` 的模型，保留 reasoning part 可以避免工具调用续写时丢失上下文：

```java
ModelMessage.assistant(List.of(
    ModelMessagePart.reasoning(ReasoningPart.builder()
        .text("需要先查询天气工具。")
        .providerMetadata(Map.of(
            "deepseek", Map.of("reasoning_content", "需要先查询天气工具。")
        ))
        .build()),
    ModelMessagePart.text("我将查询天气。")
));
```

`TOOL` 角色消息可携带 `tool-result` 或 `tool-error` part，用于调用方自己恢复一段包含工具结果的历史。普通 `USER` 和 `SYSTEM` 消息仍以文本为主；`image`、`file` 等多模态 part 当前会被拒绝。

### GenerateTextResult 返回值

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | `String` | 完整生成文本 |
| `output` | `Object` | 按 `OutputSpec` 解析并校验后的结构化输出；未启用结构化输出时为空 |
| `outputText` | `String` | 用于解析结构化输出的原始文本片段 |
| `reasoningText` | `String` | 最后一步推理文本，模型未返回推理时为空 |
| `reasoning` | `List<ReasoningPart>` | 最后一步推理 part 列表，可包含 provider metadata |
| `content` | `List<GenerationContentPart>` | 生成内容 part 列表，可能包含 `reasoning`、`text`、`tool-call`、`tool-result`、`tool-error` |
| `finishReason` | `FinishReason` | 统一结束原因，如 `STOP`、`LENGTH`、`CONTENT_FILTER`、`UNKNOWN` |
| `rawFinishReason` | `String` | 提供商或 Spring AI 返回的原始结束原因 |
| `usage` | `LanguageModelUsage` | 最后一步 token 使用量，可能为空 |
| `totalUsage` | `LanguageModelUsage` | 所有步骤累计 token 使用量；当前单步调用通常与 `usage` 相同 |
| `warnings` | `List<GenerationWarning>` | 非致命警告，例如提供商忽略或不支持某些设置 |
| `request` | `GenerationRequestMetadata` | 请求侧元数据，例如模型或 provider 信息 |
| `response` | `GenerationResponseMetadata` | 响应侧元数据，例如响应 ID、模型、响应消息、headers/body 摘要等 |
| `steps` | `List<GenerationStep>` | 每次模型调用的步骤详情，包含该步文本、工具调用、工具结果、工具错误、warning 和元数据 |
| `toolCalls` | `List<ToolCall>` | 所有步骤中的工具调用聚合，便于调用方直接读取完整工具轨迹 |
| `toolResults` | `List<ToolResult>` | 所有步骤中的工具执行结果聚合 |
| `toolErrors` | `List<ToolError>` | 所有步骤中的工具执行错误聚合 |
| `providerMetadata` | `Map<String, Object>` | 可序列化的提供商元数据 |

`LanguageModelUsage` 除了 `inputTokens`、`outputTokens`、`totalTokens`，还会在提供商返回时包含 `reasoningTokens`。

`GenerationStep` 会记录 `stepIndex`、`text`、`output`、`outputText`、`reasoningText`、`reasoning`、`content`、`finishReason`、`usage`、`toolCalls`、`toolResults`、`toolErrors`、`warnings`、`request`、`response` 和 `providerMetadata`。普通文本生成通常只有 `stepIndex = 0`；带工具且 `stopWhen` 允许继续时，每次模型调用都会形成一个 step。

### TextStreamPart 事件

`streamText()` 返回 `StreamTextResult`。对于 Console SSE 或需要完整 Halo 协议的调用方，读取 `fullStream()`；只需要最终回答文本时读取 `textStream()`；需要结构化流式视图时读取 `partialOutputStream()` 或 `elementStream()`；需要最终聚合结果时读取 `result()`。

```java
StreamTextResult stream = model.streamText(request);

stream.textStream()
    .subscribe(System.out::print);

stream.result()
    .subscribe(result -> System.out.println(result.getFinishReason()));
```

`fullStream()` 的常见事件顺序如下：

```text
start
start-step
reasoning-start?
reasoning-delta*
reasoning-end?
text-start?
text-delta*
text-end?
tool-call?
tool-result?/tool-error?
finish-step
start-step?
...
finish
```

| `type` | 说明 |
|--------|------|
| `start` | 一条模型响应开始，包含 `messageId` |
| `start-step` | 一次模型调用步骤开始，包含 `stepIndex` |
| `text-start` | 一个文本块开始，包含文本块 `id` |
| `text-delta` | 增量文本，读取 `delta` |
| `text-end` | 文本块结束 |
| `reasoning-start` | 一个推理块开始，包含推理块 `id` |
| `reasoning-delta` | 增量推理内容，读取 `delta`，不要混入最终回答文本 |
| `reasoning-end` | 推理块结束 |
| `tool-call` | 模型请求调用工具，包含 `toolCallId`、`toolName` 和 `input` |
| `tool-result` | 服务端工具执行成功，包含 `toolCallId`、`toolName` 和 `result` |
| `tool-error` | 服务端工具执行失败或模型请求未知工具，包含 `toolCallId`、`toolName` 和 `errorText` |
| `finish-step` | 当前步骤结束，包含 `finishReason`、可选 `usage`、`warnings`、`request`、`response` 和 `providerMetadata` |
| `finish` | 整体生成结束，包含 `finishReason` 和可选累计 `usage` |
| `raw` | 脱敏后的原始诊断信息，只有适配器提供安全数据时才会出现 |
| `abort` | 调用被中止时的协议事件，调用方可按结束事件处理 |
| `error` | 流式生成出错，读取 `errorText` |

## 嵌入模型调用

### 简单嵌入

```java
EmbeddingModel model = aiModelService.embeddingModel("openai-prod-text-embedding-3-small").block();

model.embedQuery("这是一段需要向量化的文本")
    .subscribe(embedding -> {
        // embedding 是 float[] 数组
        System.out.println("维度: " + embedding.length);
    });
```

### 批量嵌入

```java
EmbeddingModel model = aiModelService.embeddingModel("openai-prod-text-embedding-3-small").block();

List<String> texts = List.of("文本1", "文本2", "文本3");

model.embed(texts)
    .subscribe(response -> {
        List<float[]> embeddings = response.getEmbeddings();
        for (float[] embedding : embeddings) {
            System.out.println("维度: " + embedding.length);
        }
    });
```

### 高级嵌入请求

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .inputs(List.of("文本1", "文本2"))
    .dimensions(1536)          // 指定输出维度
    .maxBatchSize(100)         // 每批最大数量
    .providerOptions(Map.of()) // 提供商特定选项
    .build();

model.embed(request)
    .subscribe(response -> {
        // 处理结果
    });
```

### EmbeddingModel 辅助方法

```java
int maxBatch = model.maxEmbeddingsPerCall();  // 每次调用最大嵌入数量
boolean parallel = model.supportsParallelCalls(); // 是否支持并行调用
```

## 列出模型和提供商

```java
// 列出所有模型
aiModelService.listModels()
    .subscribe(models -> {
        for (ModelInfo model : models) {
            System.out.println(model.getName() + " - " + model.getDisplayName());
        }
    });

// 列出所有提供商
aiModelService.listProviders()
    .subscribe(providers -> {
        for (ProviderInfo provider : providers) {
            System.out.println(provider.getName() + " - " + provider.getDisplayName() + " (" + provider.getPhase() + ")");
        }
    });
```

## 异常处理

| 异常 | 说明 | 处理方式 |
|------|------|---------|
| `ModelNotFoundException` | 模型不存在 | 检查 modelName 是否正确，或引导用户配置模型 |
| `ProviderDisabledException` | 提供商未启用 | 提示用户启用对应提供商 |
| `ProviderApiException` | 提供商 API 调用失败 | 通常为网络或密钥问题，记录日志并提示用户检查配置 |
| `StructuredOutputValidationException` | 模型文本或工具结果不满足 `OutputSpec`/工具 schema | 读取 `outputType`、`validationPath`、`stepIndex`、`usage` 和 `response` 定位问题；不要把部分输出快照当作最终成功 |

对于非致命降级，`GenerateTextResult.warnings`、`GenerationStep.warnings` 和流式 `finish-step.warnings` 会携带稳定 `code`。常见 warning 包括结构化输出只能通过 prompt 引导、strict schema 不能由 provider 原生保证、`toolChoice=REQUIRED` 不能由默认工具适配器强制执行、工具 input examples 被忽略等。调用方可以记录这些 warning，或在对确定性要求较高的场景主动失败。

## 完整示例

```java
@Service
@RequiredArgsConstructor
public class MyAiService {

    private final ExtensionGetter extensionGetter;

    public Mono<String> summarize(String content) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(aiModelService -> aiModelService.languageModel("deepseek-prod-deepseek-chat"))
            .flatMap(model -> model.generateText("请总结以下内容：\n" + content))
            .map(GenerateTextResult::getText);
    }

    public Mono<float[]> vectorize(String text) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(aiModelService -> aiModelService.embeddingModel("openai-prod-text-embedding-3-small"))
            .flatMap(model -> model.embedQuery(text));
    }
}
```

## 注意事项

1. **确保本插件已启用**：调用方插件应在 `plugin.yaml` 中声明 `pluginDependencies.ai-foundation`，并通过 `ExtensionGetter.getEnabledExtension(AiModelService.class)` 获取服务；调用 `languageModel()` 或 `embeddingModel()` 时，如果对应的提供商未启用，会通过 `Mono` error channel 抛出 `ProviderDisabledException`
2. **异步 API**：`languageModel()` 和 `embeddingModel()` 返回 `Mono`，需要在响应式上下文中通过 `.flatMap()` 链式调用，或在非响应式上下文中调用 `.block()` 获取实例。`generateText()`、`embed()` 等方法返回 `Mono`/`Flux`；`streamText()` 会立即返回 `StreamTextResult`，其中的各个视图再通过 `Flux`/`Mono` 订阅
3. **模型名称**：使用 `listModels()` 获取准确的 `name` 字段，不要硬编码 modelId
4. **跨插件调用**：由于 Halo 插件 ApplicationContext 隔离，不能通过 `@Autowired` 注入 `AiModelService`，请使用 Halo 的 `ExtensionGetter` 获取启用的 `AiModelService` 扩展实现
