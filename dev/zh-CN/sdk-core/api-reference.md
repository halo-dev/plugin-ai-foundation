# SDK Core：公开 API 索引

简体中文 | [English](../../en/sdk-core/api-reference.md)

本页用于按类型名查询 `run.halo.aifoundation:api` 的完整公开面。第一次接入建议先阅读
[快速开始](./getting-started.md)，再按具体场景阅读主题指南；遇到源码中的类型名时，可回到本页
定位它所属的工作流。

> 本页只收录 `api/src/main/java/run/halo/aifoundation/` 中的公开顶层类型。Provider 适配器、
> Spring Bean 和 Console endpoint 属于 AI Foundation 插件实现，不是调用方 SDK。

## 服务入口与模型信息

| 类型             | 用途                                                              |
| ---------------- | ----------------------------------------------------------------- |
| `AiModelService` | 跨插件解析默认或指定名称的语言、Embedding、Rerank、图像生成模型。 |
| `ModelInfo`      | 已解析模型的只读身份和模型级信息。                                |
| `ProviderInfo`   | 已解析模型所属 Provider 的只读信息。                              |

`AiModelService` 的八个解析方法分为四组：`languageModel`、`embeddingModel`、
`rerankingModel`、`imageGenerationModel`，每组都提供无参默认模型和接收
`AiModel.metadata.name` 的命名重载。

## Capability

| 类型                         | 用途                                                       |
| ---------------------------- | ---------------------------------------------------------- |
| `ModelCapabilities`          | 已解析模型的 Provider-neutral 有效能力快照。               |
| `LanguageCapability`         | 语言模型的图片、文件、推理历史和媒体输入能力。             |
| `ImageGenerationCapability`  | 图像模型的文生图、编辑、蒙版、尺寸、宽高比和输出格式能力。 |
| `ModelCapabilityRequirement` | 表达“全部满足”的正向能力要求。                             |
| `ModelCapabilitySources`     | 记录各能力域的信息来源。                                   |
| `CapabilityDomain`           | 能力快照的顶层域。                                         |
| `CapabilitySource`           | 能力域来源，例如内建、发现或管理员配置。                   |
| `InputSource`                | 调用方媒体内容来源：data 或 Provider 原生 URL。            |

模型选择器使用同一套 capability 语义，详见
[FormKit：AI 模型选择器](../model-selector.md)。

## Agent 运行时

| 类型                                   | 用途                                                                            |
| -------------------------------------- | ------------------------------------------------------------------------------- |
| `Agent<O>`                             | 不可变、可复用的类型化 Agent 定义及 `generate` / `stream` 入口。                |
| `AgentOptions<O>`                      | 模型、instructions、工具、输出、步骤、恢复、采样和默认运行控制。                |
| `AgentCall<O>`                         | 单次 prompt 或 messages、类型化 options、metadata、context、取消和 middleware。 |
| `AgentCallValidator<O>`                | 在 Provider 调用前校验端点拥有的类型化 options。                                |
| `AgentCallPrepare<O>`                  | 一次性异步修改当前调用请求或替换当前调用模型。                                  |
| `AgentCallPrepareContext<O>`           | 当前调用、options、基础模型和新请求 builder。                                   |
| `PreparedAgentCall`                    | 调用准备后的有效模型与请求。                                                    |
| `AgentCallPhase`、`AgentCallException` | 区分 validation 与 preparation 阶段的失败。                                     |

Agent 在没有显式 stop condition 时最多执行 20 步；直接 `LanguageModel` 默认行为不变。完整构造、
恢复、UI Message 端点和持久化边界见 [Agent 运行时](./agents.md)。

## 文本生成

### 模型、请求与结果

| 类型                         | 用途                                                         |
| ---------------------------- | ------------------------------------------------------------ |
| `LanguageModel`              | `generateText`、`streamText` 与只读模型信息入口。            |
| `GenerateTextRequest`        | 文本生成请求，包括消息、采样、推理、工具、输出、控制和回调。 |
| `GenerateTextResult`         | 最终文本、结构化输出、步骤、工具、来源、用量和元数据。       |
| `StreamTextResult`           | 同一次流式请求的增量、聚合结果和 UI Message 投影。           |
| `GenerationStep`             | 一次模型调用步骤的完整结果。                                 |
| `FinishReason`               | 标准化结束原因。                                             |
| `GenerationWarning`          | 请求成功但需要调用方关注的非致命诊断。                       |
| `GenerationRequestMetadata`  | SDK 请求 ID、模型 ID 和调用方 metadata 等请求侧信息。        |
| `GenerationResponseMetadata` | Provider 响应头、原始响应和最终消息等响应侧信息。            |
| `LanguageModelUsage`         | 单步或累计 token 用量。                                      |
| `LanguageModelCapabilities`  | 已解析语言模型的推理历史和细粒度能力。                       |

`GenerateTextRequest` 的公开配置按职责分组如下：

| 分组                  | Builder 字段                                                                          |
| --------------------- | ------------------------------------------------------------------------------------- |
| 输入                  | `system`、`prompt`、`messages`                                                        |
| 输出限制              | `maxOutputTokens`、`stopSequences`                                                    |
| 采样                  | `temperature`、`topP`、`topK`、`minP`、`seed`                                         |
| 惩罚与概率            | `presencePenalty`、`frequencyPenalty`、`repetitionPenalty`、`logprobs`、`topLogprobs` |
| Provider-neutral 行为 | `reasoning`、`parallelToolCalls`、`maxRetries`、`headers`                             |
| 结构化输出            | `output`                                                                              |
| 工具与步骤            | `tools`、`toolChoice`、`stopWhen`、`prepareStep`、`toolCallRepair`                    |
| 可观测与控制          | `metadata`、`context`、`lifecycle`、`cancellationToken`、`timeouts`、`middleware`     |

`StreamTextResult` 可从同一次请求读取：

- 增量：`fullStream()`、`textStream()`、`partialOutputStream()`、`elementStream()`。
- 聚合：`output()`、`result()`、`text()`、`reasoningText()`、`content()`、`sources()`。
- 诊断：`finishReason()`、`rawFinishReason()`、`usage()`、`totalUsage()`、`warnings()`、
  `request()`、`response()`、`providerMetadata()`。
- 多步骤：`steps()`、`responseMessages()`、`toolCalls()`、`toolResults()`、`toolErrors()`。
- UI：`toUIMessageStream()`、`toUIMessageStreamResponse(...)`。

### Reasoning、步骤与超时

| 类型                  | 用途                                                                      |
| --------------------- | ------------------------------------------------------------------------- |
| `ReasoningOptions`    | `providerDefault()`、`enabled()`、`disabled()` 或指定 effort 的推理偏好。 |
| `StopCondition`       | 决定工具调用后是否继续下一模型步骤。                                      |
| `PrepareStepCallback` | 在每一步调用模型前返回请求覆盖项。                                        |
| `PreparedStep`        | 当前步骤的 messages、工具、tool choice、输出和采样覆盖项。                |
| `StepContext`         | `prepareStep` 与停止条件可读取的不可变步骤上下文。                        |
| `GenerationTimeouts`  | 总调用、单个模型步骤和服务端工具的超时。                                  |
| `CancellationSource`  | 创建并触发调用方拥有的取消信号。                                          |
| `CancellationToken`   | 传入请求或 UI Message stream 的取消信号。                                 |

### Middleware

| 类型                           | 用途                                  |
| ------------------------------ | ------------------------------------- |
| `LanguageModelMiddleware`      | 转换请求并包装非流式或流式模型调用。  |
| `LanguageModelMiddlewares`     | 组合 middleware、包装模型或移除包装。 |
| `LanguageModelRequestContext`  | 请求转换阶段共享的模型和请求上下文。  |
| `LanguageModelGenerateContext` | 非流式调用上下文。                    |
| `LanguageModelStreamContext`   | 流式调用上下文。                      |
| `GenerateTextNext`             | 非流式 middleware 链的下一步。        |
| `StreamTextNext`               | 流式 middleware 链的下一步。          |

模型级 middleware 包装 `LanguageModel`；请求级 middleware 放入
`GenerateTextRequest.middleware(...)`。顺序、RAG 组合和示例见
[Middleware、步骤控制与生命周期](./middleware-and-lifecycle.md)。

### 生命周期

| 类型                                 | 触发时机                               |
| ------------------------------------ | -------------------------------------- |
| `GenerationLifecycle`                | 实现请求、步骤、工具和错误回调的入口。 |
| `GenerationStartEvent`               | 整个生成请求开始。                     |
| `GenerationFinishEvent`              | 整个生成请求成功完成。                 |
| `GenerationErrorEvent`               | 整个生成请求失败。                     |
| `GenerationStepStartEvent`           | 一个模型步骤开始。                     |
| `GenerationStepFinishEvent`          | 一个模型步骤完成。                     |
| `GenerationToolCallStartEvent`       | 服务端工具开始执行。                   |
| `GenerationToolCallFinishEvent`      | 服务端工具成功或失败后结束。           |
| `GenerationToolApprovalRequestEvent` | 工具等待调用方审批。                   |

生命周期回调用于观测，不应用来替代业务结果；回调本身失败时会转换为 warning。

## 消息、内容、媒体与来源

| 包        | 类型                                                         | 用途                                                        |
| --------- | ------------------------------------------------------------ | ----------------------------------------------------------- |
| `message` | `ModelMessage`、`ModelMessageRole`                           | Provider-neutral 对话消息和角色。                           |
| `message` | `ModelMessagePart`、`run.halo.aifoundation.message.TextPart` | 文本、图片、文件、推理、工具调用与工具结果等模型消息内容。  |
| `media`   | `DataContent`                                                | URL、byte 数组或 base64 媒体输入。                          |
| `media`   | `GeneratedFile`                                              | 图像结果中的 URL 或 base64 文件。                           |
| `part`    | `GenerationContentPart`、`PartType`                          | 文本生成结果中的统一内容 part 与类型。                      |
| `part`    | `run.halo.aifoundation.part.ReasoningPart`                   | 聚合后的可见推理内容和 Provider metadata。                  |
| `part`    | `TextStreamPart`                                             | `fullStream()` 中的文本、推理、工具、来源、文件和终止事件。 |
| `source`  | `SourceReference`、`SourceReferences`                        | 统一来源引用及从 stream part 提取来源的辅助方法。           |
| `source`  | `RetrievedSource`、`RetrievedContext`                        | 调用方检索和 RAG middleware 使用的来源与上下文。            |

同名 `TextPart`、`ReasoningPart` 在不同包中职责不同：`message` / `part` 面向模型生成，
`ui` 包中的类型面向界面持久化。

## 结构化输出

| 类型               | 用途                                     |
| ------------------ | ---------------------------------------- |
| `OutputSpec`       | 指定对象、数组或枚举输出及其 schema。    |
| `OutputType`       | 结构化输出类型。                         |
| `JsonSchema`       | Provider-neutral JSON Schema 对象。      |
| `StructuredSchema` | JSON Schema 与本地解析、校验逻辑的组合。 |

`GenerateTextResult.getOutput()` 和 `StreamTextResult.output()` 返回解析后的最终值；数组输出还可
消费 `elementStream()`。完整示例见
[生成结构化数据](./generating-structured-data.md)。

## 工具与多步骤

| 类型                                                                                             | 用途                                                         |
| ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| `ToolDefinition`                                                                                 | 工具名、描述、输入 schema、执行器、审批、strict 和输入示例。 |
| `ToolChoice`                                                                                     | auto、none、required 或指定工具。                            |
| `ToolExecutor`                                                                                   | 在 AI Foundation 后端执行工具。                              |
| `ToolExecutionContext`                                                                           | 工具执行时可读取的调用 ID、消息、metadata 和 context。       |
| `ToolCall`、`ToolResult`、`ToolError`                                                            | 模型请求、成功输出和失败输出的持久结果。                     |
| `ToolInputParseError`                                                                            | 工具输入无法按 schema 解析。                                 |
| `ToolApprovalPolicy`、`ToolApprovalPredicate`                                                    | 声明工具是否总是、从不或按输入等待审批。                     |
| `ToolApprovalRequest`、`ToolApprovalResponse`                                                    | 跨请求保存审批请求和调用方决定。                             |
| `ToolCallFailureKind`、`ToolCallRepairCallback`、`ToolCallRepairContext`、`ToolCallRepairResult` | 已知工具无效输入与未知/更名工具的统一恢复契约。              |
| `ToolInputStartCallback`、`ToolInputStartContext`                                                | 观察工具输入开始。                                           |
| `ToolInputDeltaCallback`、`ToolInputDeltaContext`                                                | 观察工具输入文本增量。                                       |
| `ToolInputAvailableCallback`、`ToolInputAvailableContext`                                        | 观察完整且已解析的工具输入。                                 |

`ToolDefinition` 的主要 builder 字段为 `name`、`description`、`inputSchema`、`executor`、
`strict`、`inputExamples`、`approvalPolicy` 和 `approvalPredicate`。服务端执行、外部工具、
审批双请求、修复和历史保存见
[工具调用与多步骤](./tools-and-tool-calling.md)。

## Embedding

| 类型                                                                 | 用途                                                           |
| -------------------------------------------------------------------- | -------------------------------------------------------------- |
| `EmbeddingModel`                                                     | 单条 query/value、批量 value 和高级请求入口。                  |
| `EmbeddingRequest`                                                   | 输入、维度、批大小、并发、重试、header、生命周期、取消和超时。 |
| `EmbeddingResponse`                                                  | 保序向量、用量、warning 和响应元数据。                         |
| `EmbeddingUtils`                                                     | 余弦相似度等向量辅助方法。                                     |
| `EmbeddingLifecycle`                                                 | 高级请求的开始、完成和错误回调。                               |
| `EmbeddingStartEvent`、`EmbeddingFinishEvent`、`EmbeddingErrorEvent` | Embedding 生命周期事件。                                       |
| `EmbeddingUsage`、`EmbeddingWarning`、`EmbeddingResponseMetadata`    | 用量、非致命诊断和安全响应元数据。                             |

常用方法是 `embedQuery`、`embedValue` 和 `embedValues`；需要 `dimensions`、
`maxBatchSize`、`maxParallelCalls` 或控制信号时使用 `EmbeddingRequest`。

## Rerank 与 RAG

### Rerank

| 类型                                                     | 用途                                                     |
| -------------------------------------------------------- | -------------------------------------------------------- |
| `RerankingModel`                                         | 简单字符串列表或高级请求的重排入口。                     |
| `RerankRequest`                                          | query、候选文档、`topN`、metadata、context、取消和超时。 |
| `RerankDocument`                                         | 带文本和调用方 metadata 的候选文档。                     |
| `RerankResponse`                                         | 排名结果、用量、warning 和响应元数据。                   |
| `RerankResult`                                           | 原始文档索引、分数和文档内容。                           |
| `RerankUsage`、`RerankWarning`、`RerankResponseMetadata` | 用量、非致命诊断和响应元数据。                           |

### RAG middleware

| 类型                                | 用途                                      |
| ----------------------------------- | ----------------------------------------- |
| `RagRetriever`                      | 按当前请求检索 `RetrievedContext`。       |
| `RagLanguageModelMiddleware`        | 在模型调用前执行检索并注入上下文。        |
| `RagMiddlewares`                    | 创建和组合 RAG middleware。               |
| `RagMiddlewareOptions`              | 检索、注入、重排、失败和 UI 暴露策略。    |
| `RagRetrievalRequest`               | 传给 retriever 的查询、请求和步骤上下文。 |
| `RagSourceReranker`                 | 对检索来源进行业务自定义重排。            |
| `RerankingModelRagSourceReranker`   | 使用 `RerankingModel` 重排来源。          |
| `RagSourceRerankRequest`            | 来源重排请求。                            |
| `RagPromptPlacement`                | 将上下文放在 system 或 user 内容。        |
| `RagFailurePolicy`                  | 检索或重排失败时 fail 或 continue。       |
| `RagEmptyContextPolicy`             | 没有检索结果时的处理策略。                |
| `RagLifecycle`、`RagLifecycleEvent` | 检索、重排和注入阶段的观测入口与事件。    |

完整组合示例见
[Embedding、Rerank 与 RAG](./embeddings-reranking-and-rag.md)。

## 图像生成

| 类型                                                | 用途                                                                                |
| --------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `ImageGenerationModel`                              | 文生图、图生图和编辑请求入口。                                                      |
| `GenerateImageRequest`                              | prompt、输入图、蒙版、数量、尺寸、宽高比、negative prompt、seed、输出格式和控制项。 |
| `GenerateImageResult`                               | 生成文件、warning、用量和 Provider metadata。                                       |
| `ImageResponseFormat`                               | URL 或 base64 响应偏好。                                                            |
| `ImageUsage`、`ImageGenerationWarning`              | 图像调用用量与非致命诊断。                                                          |
| `ImageGenerationRequests`、`ImageGenerationResults` | 请求复制和多次调用结果聚合辅助方法。                                                |

### 图像 middleware

| 类型                             | 用途                                   |
| -------------------------------- | -------------------------------------- |
| `ImageGenerationMiddleware`      | 转换请求并包装图像生成调用。           |
| `ImageGenerationMiddlewares`     | 组合 middleware 和包装模型。           |
| `ImageGenerationContext`         | 当前模型与图像请求上下文。             |
| `GenerateImageNext`              | middleware 链的下一步。                |
| `ImageGenerationMiddlewareAware` | 暴露已包装图像 middleware 的模型契约。 |

请求、结果保存和 middleware 示例见
[图像生成](./image-generation.md)。

## 异常

所有 SDK 业务异常都继承 `AiFoundationException`：

| 场景           | 类型                                                                                                                       |
| -------------- | -------------------------------------------------------------------------------------------------------------------------- |
| 模型解析       | `DefaultModelNotConfiguredException`、`ModelNotFoundException`、`ModelDisabledException`、`IncompatibleModelTypeException` |
| Provider 状态  | `ProviderDisabledException`、`ProviderApiException`                                                                        |
| Capability     | `UnsupportedModelCapabilityException`                                                                                      |
| 媒体           | `InvalidMediaContentException`、`MediaContentTooLargeException`                                                            |
| 文本控制       | `AiGenerationCancelledException`、`AiGenerationTimeoutException`                                                           |
| Embedding 控制 | `EmbeddingCancelledException`、`EmbeddingTimeoutException`                                                                 |
| Rerank 控制    | `RerankCancelledException`、`RerankTimeoutException`                                                                       |
| 图像           | `ImageGenerationException`                                                                                                 |
| 结构化输出     | `StructuredOutputValidationException`                                                                                      |

异常分类、响应转换和 Reactor 示例见
[错误处理](./error-handling.md)。

## Java UI Message bridge

这些类型位于 Java API 中，负责把模型流转换为前端可消费、可持久化的 Halo UI Message。
第一次实现聊天 endpoint 时优先阅读
[SDK UI：Chatbot](../sdk-ui/chatbot.md) 和
[UI Message Stream 单页参考](../ui-message-stream.md)。

### Chat handler 与请求准备

| 类型                                                  | 用途                                                                    |
| ----------------------------------------------------- | ----------------------------------------------------------------------- |
| `UIMessageChatHandlers`                               | 校验、转换、准备请求，执行模型或类型化 Agent 并创建 UI Message stream。 |
| `UIMessageChatOptions`                                | 互斥配置模型或 Agent，以及消息、middleware、校验、转换、取消和回调。    |
| `UIMessageChatRequest`、`UIMessageChatTrigger`        | 前端提交的会话、触发方式和 regenerate 目标。                            |
| `UIMessageChatPrepare`、`UIMessageChatPrepareContext` | 执行前异步补充业务请求配置。                                            |
| `UIMessageChatResult`                                 | stream、HTTP response、校验、转换和最终消息的组合结果。                 |
| `UIMessageCancellation`、`UIMessageCancellations`     | 创建调用方拥有的 token，并在订阅者取消 Flux / Mono 时联动取消模型调用。 |

`UIMessageChatOptions` 的公开链式配置为 `model` / `agent`（二选一）、`messages`、`chatRequest`、`message`、
`metadataSupplier`、`generateMessageId`、`serializer`、`request`、`prepare`、
`middleware`、`validation`、`conversion`、`onFinish`、`onError`、`onReadError`、
`cancellationToken` 和 `terminateOnError`。

Agent 端点优先使用类型安全的 `UIMessageChatHandlers.streamAgent(...)`，Transport 请求不能覆盖
Agent 的 semantic policy。详见 [Agent 运行时](./agents.md)。

### 校验与转换

| 类型                                                                             | 用途                                                           |
| -------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `UIMessageValidators`、`UIMessageValidationOptions`                              | 校验一组持久化 UI Message 并注册扩展 validator。               |
| `UIMessageValidationResult`、`UIMessageValidationIssue`                          | 校验后的消息和稳定问题描述。                                   |
| `UIMessageValidationContext`                                                     | 自定义 validator 的消息与 part 位置。                          |
| `UIMessageMetadataValidator`、`UIMessageDataValidator`、`UIMessageToolValidator` | metadata、命名 data 和工具 part validator。                    |
| `UIMessageChunkValidator`                                                        | 校验 wire chunk。                                              |
| `InvalidUIMessageException`                                                      | 严格校验失败。                                                 |
| `UIMessageConverters`、`UIMessageConversionOptions`                              | 将 UI Message 转为 `ModelMessage` 并配置策略和扩展 converter。 |
| `UIMessageConversionResult`、`UIMessageConversionWarning`                        | 转换后的消息和非致命诊断。                                     |
| `UIMessageConversionContext`                                                     | 自定义 converter 的消息与 part 位置。                          |
| `UIMessageDataConverter`、`UIMessagePartConverter`                               | 命名 data 和自定义 part converter。                            |
| `UnsupportedUIMessagePartPolicy`、`EmptyUIMessagePolicy`                         | 不可转换 part 与空消息策略。                                   |
| `UIReasoningConversion`                                                          | reasoning 历史回传策略。                                       |

`UIMessageValidationOptions` 支持 `metadataValidator`、`dataValidator` 和两种
`toolValidator`；`UIMessageConversionOptions` 支持 `unsupportedPartPolicy`、
`emptyMessagePolicy`、`reasoningConversion`、`dataConverter` 和 `partConverter`。

### Stream 创建、读取与响应

| 类型                                                    | 用途                                                  |
| ------------------------------------------------------- | ----------------------------------------------------- |
| `UIMessageStream`、`UIMessageStreamWriter`              | UI Message chunk 流和写入接口。                       |
| `UIMessageStreams`、`UIMessageStreamOptions`            | 创建、执行、合并和最终聚合 stream。                   |
| `UIMessageStreamMapper`                                 | 将 `TextStreamPart` 映射为 UI Message chunk。         |
| `UIMessageChunkReducer`                                 | 将连续 chunk 归并为持久化消息 part 和 terminal 状态。 |
| `UIMessageStreamReader`、`UIMessageStreamReaderOptions` | 把 chunk 流聚合为消息快照和最终消息。                 |
| `ReadUIMessageStreamResult`                             | `messages()` 快照流与 `responseMessage()` 最终结果。  |
| `UIMessageStreamFinish`、`UIMessageStreamTerminal`      | 最终对话、响应消息和终止状态。                        |
| `UIMessageStreamResponse`                               | HTTP 状态、header、content type 与序列化 SSE body。   |
| `UIMessageTransportCodec`                               | chunk 的默认 JSON/SSE 编解码。                        |
| `UIMessageMetadataMerger`                               | 将 metadata chunk 合并到调用方定义的消息 metadata。   |

创建选项可配置 message、原始消息、ID 生成器、metadata、错误映射、取消、结束回调和 writer；
reader 选项提供对应的聚合配置。继续已有 assistant message 时必须同时传入原消息和原始对话，
让最终回调可以正确替换而不是重复追加。

### 持久化消息 part

| 类型                                             | 用途                                          |
| ------------------------------------------------ | --------------------------------------------- |
| `UIMessage`、`UIMessageRole`                     | 可保存并在下一请求回传的消息。                |
| `UIMessagePart`、`UIMessageParts`                | 持久化 part 契约和查找、复制辅助方法。        |
| `StepStartPart`                                  | 多步骤边界标记。                              |
| `run.halo.aifoundation.ui.TextPart`              | 已聚合文本块。                                |
| `run.halo.aifoundation.ui.ReasoningPart`         | 已聚合可见推理及 Provider metadata。          |
| `DataPart`                                       | 命名的应用数据。                              |
| `SourceUrlPart`、`SourceDocumentPart`            | URL 和文档来源。                              |
| `FilePart`                                       | URL、文件 ID 或内联数据。                     |
| `ToolPart`、`ToolPartState`、`ToolApproval`      | 动态工具 part、生命周期状态和审批信息。       |
| `UIMessagePartIdentity`                          | reducer 中定位同一 part 的稳定键。            |
| `UIMessageDynamicNames`、`RagUIMessageDataNames` | 动态 data / tool 类型名和标准 RAG data 名称。 |
| `RagUIMessageOutputMode`                         | RAG 来源以 source、data 或两者输出。          |

### Wire chunks

所有 chunk 都实现 `UIMessageChunk`，`UIMessageChunkType` 提供标准类型常量，
`UIMessageChunks` 提供构造和判断辅助方法：

| 阶段                | 类型                                                                                           |
| ------------------- | ---------------------------------------------------------------------------------------------- |
| 消息与步骤开始      | `StartChunk`、`StartStepChunk`                                                                 |
| 文本                | `TextStartChunk`、`TextDeltaChunk`、`TextEndChunk`                                             |
| 推理                | `ReasoningStartChunk`、`ReasoningDeltaChunk`、`ReasoningEndChunk`                              |
| 应用数据与 metadata | `DataChunk`、`MessageMetadataChunk`                                                            |
| 来源与文件          | `SourceUrlChunk`、`SourceDocumentChunk`、`FileChunk`                                           |
| 工具输入            | `ToolInputStartChunk`、`ToolInputDeltaChunk`、`ToolInputAvailableChunk`、`ToolInputErrorChunk` |
| 工具输出            | `ToolOutputAvailableChunk`、`ToolOutputErrorChunk`                                             |
| 工具审批            | `ToolApprovalRequestChunk`、`ToolApprovalResponseChunk`                                        |
| 动态工具形态        | `ToolChunk`                                                                                    |
| 步骤与正常结束      | `FinishStepChunk`、`FinishChunk`                                                               |
| 错误与取消          | `ErrorChunk`、`AbortChunk`                                                                     |

浏览器端的对应类型、状态归并和 wire 字段见
[SDK UI：公开导出索引](../sdk-ui/api-reference.md) 与
[Stream Protocol](../sdk-ui/stream-protocol.md)。
