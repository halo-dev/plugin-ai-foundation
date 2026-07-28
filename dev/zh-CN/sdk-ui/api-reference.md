# SDK UI：公开导出索引

简体中文 | [English](../../en/sdk-ui/api-reference.md)

本页完整列出 `@halo-dev/ai-foundation-sdk` 从包入口导出的运行时 API 与 TypeScript 类型。
第一次构建聊天界面时先阅读 [Chatbot](./chatbot.md)；本页适合自动补全之外的按名称查询。

> 只有 `ui/packages/sdk/src/index.ts` 导出的符号属于包级公共契约。包内部源码中额外标记为
> `export` 的实现细节不能通过内部路径导入。

## Chat

| 导出                                                | 用途                                                         |
| --------------------------------------------------- | ------------------------------------------------------------ |
| `useChat`、`UseChatOptions`                         | Vue 聊天 composable 与配置。                                 |
| `Chat`、`ChatInit`                                  | 框架无关的聊天控制器与初始化选项。                           |
| `ChatStateAdapter`                                  | 把 `Chat` 接到自定义响应式状态容器。                         |
| `createPlainChatState`                              | 创建不依赖 Vue 的内存状态 adapter。                          |
| `SendMessageInput`                                  | `sendMessage` 接收的文本、files、metadata 或完整 parts。     |
| `ChatStatus`                                        | `submitted`、`streaming`、`ready`、`error`、`disconnected`。 |
| `createUserMessage`                                 | 从发送输入创建规范化 user `UIMessage`。                      |
| `lastAssistantMessageIsCompleteWithToolCalls`       | 判断最后一条 assistant 消息的工具调用是否进入终态。          |
| `lastAssistantMessageHasCompletedToolContinuations` | 判断客户端工具是否已提供可继续的输出。                       |
| `lastAssistantMessageHasRespondedToToolApprovals`   | 判断等待审批的工具是否已有决定。                             |

`useChat` / `Chat` 的消息发送、重新生成、停止、恢复、工具输出和审批示例见
[Chatbot](./chatbot.md) 与 [工具交互](./chatbot-tool-usage.md)。

## Transport 与请求

| 导出                                           | 用途                                                                               |
| ---------------------------------------------- | ---------------------------------------------------------------------------------- |
| `ChatTransport`                                | 自定义聊天传输的最小接口。                                                         |
| `DefaultChatTransport`                         | 读取 Halo UI Message SSE。                                                         |
| `HttpChatTransport`                            | HTTP 请求准备、header、body 和 fetch 的通用基类。                                  |
| `TextStreamChatTransport`                      | 把普通文本 response stream 映射成聊天文本 part。                                   |
| `HttpTransportOptions`                         | `api`、`headers`、`body`、`credentials`、`fetch` 和 `prepareSendMessagesRequest`。 |
| `ChatRequestOptions`                           | 单次聊天请求的 header、body、credentials 和 metadata。                             |
| `SendMessagesOptions`                          | transport 收到的 chat ID、消息、trigger、message ID 和 abort signal。              |
| `PreparedRequest`                              | 自定义请求准备函数返回的 URL、body、header 和 credentials。                        |
| `FetchFunction`                                | 可注入的 `fetch` 签名。                                                            |
| `Resolvable`                                   | 值或异步求值函数。                                                                 |
| `UIMessageChatRequest`、`UIMessageChatTrigger` | 默认后端 body 与 submit / regenerate 触发类型。                                    |
| `OpenAPIRequestArgs`、`fromOpenAPIRequestArgs` | 把生成的 OpenAPI client 请求参数接入 transport。                                   |

完整配置和自定义 transport 示例见
[Transport 与读取消息流](./transport-and-reading-streams.md)。

## UI Message

| 导出                                        | 用途                                               |
| ------------------------------------------- | -------------------------------------------------- |
| `UIMessage`、`UIMessageRole`                | 可渲染、保存并回传的消息和角色。                   |
| `UIMessagePart`                             | 所有持久化 part 的联合类型。                       |
| `StepStartPart`                             | 多步骤边界。                                       |
| `TextPart`、`ReasoningPart`                 | 已聚合的文本与可见推理块。                         |
| `DataPart`                                  | `data-*` 自定义应用数据。                          |
| `SourceUrlPart`、`SourceDocumentPart`       | URL 与文档来源。                                   |
| `FilePart`                                  | URL、文件 ID 或内联文件数据。                      |
| `ToolPart`、`ToolPartState`、`ToolApproval` | 动态工具 part、状态和审批信息。                    |
| `FinishReason`、`LanguageModelUsage`        | 标准化结束原因和 token 用量。                      |
| `UIMessageStreamTerminal`                   | stream 的 finish、usage、abort 和 error 聚合状态。 |
| `messageText`                               | 拼接一条消息中的所有文本 part。                    |
| `generateId`、`IdGenerator`                 | 默认 ID 生成器及其函数类型。                       |

`providerMetadata` 是 Provider 返回的只读状态；保存消息时应原样保留，不应把它当作调用方配置。

## Chunk reducer

| 导出                     | 用途                                          |
| ------------------------ | --------------------------------------------- |
| `applyUIMessageChunk`    | 将一个 chunk 应用到现有 reducer state。       |
| `createUIMessageReducer` | 创建可持续消费 chunk 的 reducer。             |
| `CreateReducerOptions`   | 初始消息、ID、metadata 和 terminal 回调配置。 |
| `UIMessageReducerState`  | 当前消息、终止状态和内部增量缓冲。            |

Chat transport 和 `readUIMessageStream` 已使用相同 reducer；只有直接处理自定义 chunk 源时才需
手动调用这些底层入口。

## Stream 读取

| 导出                                                | 用途                                                          |
| --------------------------------------------------- | ------------------------------------------------------------- |
| `readUIMessageStream`、`ReadUIMessageStreamOptions` | 把 `AsyncIterable<UIMessageChunk>` 读取为消息快照和最终状态。 |
| `UIMessageStreamReadResult`                         | 最终 assistant 消息、terminal、读取状态和可选错误。           |
| `UIMessageStreamReadStatus`                         | 当前读取状态。                                                |
| `UIMessageStreamFinishEvent`                        | 结束回调收到的最终消息和 terminal。                           |
| `readUIMessageSSEStream`                            | 从 Halo SSE response 逐个解析 `UIMessageChunk`。              |
| `readTextStream`                                    | 从普通文本 response 读取字符串增量。                          |
| `collectText`                                       | 将字符串异步迭代器聚合成完整文本。                            |
| `assertHaloUIMessageStreamResponse`                 | 当 response 带协议版本 header 时校验其值。                    |
| `HALO_UI_MESSAGE_STREAM_HEADER`                     | 协议版本 header 名。                                          |
| `HALO_UI_MESSAGE_STREAM_VERSION`                    | 协议版本值。                                                  |
| `DONE_MARKER`                                       | SSE 正常结束标记。                                            |

读取已有 assistant message 时，通过 `ReadUIMessageStreamOptions` 传入 `message`。输入可以是
`stream`、`readableStream` 或 `response`；还可以配置 ID、metadata、runtime schema、
`abortSignal`、各阶段回调和 `throwOnError`。详见
[Transport 与读取消息流](./transport-and-reading-streams.md)。

## Stream chunk

`UIMessageChunk` 是以下 wire 类型的联合：

| 阶段                | 导出                                                                                           |
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

各字段、顺序约束、持久化行为和 SSE 示例见
[Stream Protocol](./stream-protocol.md)。

## 工具输入与续跑

| 导出                        | 用途                                                   |
| --------------------------- | ------------------------------------------------------ |
| `ToolOutputSuccessInput`    | 通过 `result` 或 `output` 字段提交工具调用的成功结果。 |
| `ToolOutputErrorInput`      | 为工具调用提交 `output-error`。                        |
| `ToolOutputInput`           | 成功或失败工具输出的联合类型。                         |
| `ToolApprovalResponseInput` | 按 approval ID 或 tool call ID 提交审批决定。          |

`Chat.addToolOutput` 和 `Chat.addToolApprovalResponse` 使用这些类型。是否自动再次请求后端由
`sendAutomaticallyWhen` 明确决定，详见
[工具交互](./chatbot-tool-usage.md)。

## 文件输入

| 导出                                              | 用途                                |
| ------------------------------------------------- | ----------------------------------- |
| `BrowserFileInput`                                | 浏览器 `File` 或兼容的文件输入。    |
| `filePartFromFile`、`FilePartFromFileOptions`     | 把单个浏览器文件转换为 `FilePart`。 |
| `filePartsFromFiles`、`FilePartsFromFilesOptions` | 按顺序转换多个文件。                |

helper 会读取文件并写入纯 base64 `data`。若业务使用对象存储或 Halo 附件，调用方应先上传，
再自行构造带 `url` 的 `FilePart`。消息发送与限制示例见
[Chatbot](./chatbot.md)。

## 持久化与校验

| 导出                                              | 用途                                                        |
| ------------------------------------------------- | ----------------------------------------------------------- |
| `validateUIMessages`、`ValidateUIMessagesOptions` | 检查消息结构和 runtime schema，返回所有 validation issues。 |
| `assertValidUIMessages`                           | 严格校验；发现问题时抛出异常。                              |
| `UIMessageValidationIssue`                        | 带消息、part 位置和稳定 code 的问题。                       |
| `AIUIMessageValidationError`                      | 严格消息校验异常。                                          |
| `pruneMessages`、`PruneMessagesOptions`           | 保留最近消息并按配置移除 pending 工具 part。                |

保存前校验、恢复和安全裁剪见
[消息持久化](./chatbot-message-persistence.md)。

## Runtime schema 与部分 JSON

| 导出                                                                          | 用途                                        |
| ----------------------------------------------------------------------------- | ------------------------------------------- |
| `SchemaLike`                                                                  | JSON Schema 或 Standard Schema 兼容对象。   |
| `StandardSchemaLike`、`StandardSchemaValidationResult`、`StandardSchemaIssue` | Standard Schema 运行时校验契约。            |
| `RuntimeSchemaValidationContext`                                              | 自定义 schema 校验的上下文。                |
| `DataPartSchemas`、`MessageMetadataSchema`                                    | 命名 data 与 message metadata schema 映射。 |
| `JsonSchema`、`jsonSchema`、`toJsonSchema`                                    | JSON Schema 类型、帮助函数和规范化转换。    |
| `validateRuntimeSchema`、`validateFinalValue`                                 | 校验 stream 中间值或最终值。                |
| `parsePartialJson`、`fixJson`                                                 | 尽力解析尚未结束的 JSON 文本。              |
| `DeepPartial`                                                                 | 增量对象状态的递归可选类型。                |

这些入口用于 `experimental_useObject`、持久化校验和自定义 data / metadata。示例见
[Completion 与 Object stream](./completion-and-object-generation.md) 和
[自定义数据与 Metadata](./streaming-data-and-metadata.md)。

## Completion 与 Object

| 导出                                         | 用途                                                 |
| -------------------------------------------- | ---------------------------------------------------- |
| `useCompletion`、`UseCompletionOptions`      | Vue 文本补全状态。                                   |
| `CompletionRequestOptions`                   | 单次 completion 请求的 header、body 和 credentials。 |
| `experimental_useObject`、`UseObjectOptions` | Vue 增量 JSON 对象状态。                             |
| `ObjectRequestOptions`                       | 单次 object 请求的 header、body 和 credentials。     |

这两个 composable 消费普通文本 / JSON 文本 response，不消费 Halo UI Message SSE。后端应使用
与 endpoint 对应的 content type，详见
[Completion 与 Object stream](./completion-and-object-generation.md)。

## 错误

| 导出                                                             | 用途                                |
| ---------------------------------------------------------------- | ----------------------------------- |
| `AIUIError`                                                      | SDK UI 错误基类。                   |
| `AIUIProtocolError`                                              | HTTP、SSE 或协议约束错误。          |
| `AIUISchemaValidationError`                                      | runtime schema 校验错误。           |
| `AIUISchemaValidationErrorOptions`、`AIUISchemaValidationTarget` | schema 错误的目标、路径和问题信息。 |
| `isProtocolError`                                                | 类型安全判断协议错误。              |

取消请求使用标准 `AbortSignal`；Chat 会把主动停止与普通传输错误区分处理。
