# SDK Core

简体中文 | [English](../../en/sdk-core/README.md)

SDK Core 是 AI Foundation 的 Provider-neutral Java API。调用方只依赖
`run.halo.aifoundation:api`，由 Halo 管理员决定具体 Provider、模型与统一参数到供应方参数的
映射。

## 典型调用链

```text
ExtensionGetter
  -> AiModelService
  -> LanguageModel / EmbeddingModel / RerankingModel / ImageGenerationModel
  -> request
  -> Mono / Flux result
```

`modelName` 始终是 `AiModel.metadata.name`，不是 Provider 类型，也不是供应方原始模型 ID。

## 指南

1. [快速开始](./getting-started.md)
2. [生成与流式文本](./generating-text.md)
3. [生成结构化数据](./generating-structured-data.md)
4. [工具调用与多步骤](./tools-and-tool-calling.md)
5. [Agent 运行时](./agents.md)
6. [Embedding、Rerank 与 RAG](./embeddings-reranking-and-rag.md)
7. [图像生成](./image-generation.md)
8. [Middleware、步骤控制与生命周期](./middleware-and-lifecycle.md)
9. [错误处理](./error-handling.md)
10. [完整公开 API 索引](./api-reference.md)

## API 入口速览

| 需求       | 主要类型                                                          |
| ---------- | ----------------------------------------------------------------- |
| 解析模型   | `AiModelService`                                                  |
| 生成文本   | `LanguageModel`、`GenerateTextRequest`、`GenerateTextResult`      |
| 流式生成   | `StreamTextResult`、`TextStreamPart`                              |
| 消息       | `ModelMessage`、`ModelMessagePart`、`DataContent`                 |
| 结构化输出 | `OutputSpec`、`JsonSchema`、`StructuredSchema`                    |
| 工具       | `ToolDefinition`、`ToolChoice`、`StopCondition`、`PreparedStep`   |
| Agent      | `Agent`、`AgentOptions`、`AgentCall`、`PreparedAgentCall`         |
| Embedding  | `EmbeddingModel`、`EmbeddingRequest`、`EmbeddingUtils`            |
| Rerank     | `RerankingModel`、`RerankRequest`                                 |
| RAG        | `RagRetriever`、`RagMiddlewares`、`RagMiddlewareOptions`          |
| 图像       | `ImageGenerationModel`、`GenerateImageRequest`、`GeneratedFile`   |
| 控制       | `CancellationSource`、`GenerationTimeouts`、`GenerationLifecycle` |
| UI bridge  | `UIMessageChatHandlers`、`UIMessageStreamResponse`、`streamAgent` |

需要按类型名查找完整公开面时，使用
[SDK Core：公开 API 索引](./api-reference.md)。

## 设计约束

- API 是响应式的：非流式结果通常为 `Mono<T>`，事件流为 `Flux<T>`。
- 调用方通过统一请求字段使用模型，Provider client 与供应方参数映射由插件内部管理。
- `prompt` 与 `messages` 二选一；`system` 可与任意一种输入搭配。
- `responseMessages` 是继续多步骤或多轮上下文的权威结果，不能只保存最终文本。
- `warnings` 表示调用完成但存在可恢复差异；异常表示请求没有按约定成功。
- 多媒体、工具、结构化输出、推理和图像能力依赖所选模型的真实 capability。

返回 [开发者文档首页](../README.md)。
