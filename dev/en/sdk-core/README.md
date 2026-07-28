# SDK Core

[简体中文](../../zh-CN/sdk-core/README.md) | English

SDK Core is the provider-neutral Java API published as `run.halo.aifoundation:api`. Consumer
plugins depend on this module; Halo administrators choose providers, models, capabilities, and
parameter mappings.

`modelName` always means `AiModel.metadata.name`, not a provider type or provider-side model ID.

## Guides

1. [Getting started](./getting-started.md)
2. [Generating and streaming text](./generating-text.md)
3. [Structured output](./generating-structured-data.md)
4. [Tools and multi-step generation](./tools-and-tool-calling.md)
5. [Embeddings, reranking, and RAG](./embeddings-reranking-and-rag.md)
6. [Image generation](./image-generation.md)
7. [Middleware, step control, and lifecycle](./middleware-and-lifecycle.md)
8. [Error handling](./error-handling.md)
9. [Complete public API index](./api-reference.md)

## Entry points

| Need                  | Main types                                                      |
| --------------------- | --------------------------------------------------------------- |
| Resolve a model       | `AiModelService`                                                |
| Generate text         | `LanguageModel`, `GenerateTextRequest`, `GenerateTextResult`    |
| Stream text           | `StreamTextResult`, `TextStreamPart`                            |
| Structured output     | `OutputSpec`, `JsonSchema`, `StructuredSchema`                  |
| Tools                 | `ToolDefinition`, `ToolChoice`, `StopCondition`, `PreparedStep` |
| Embeddings            | `EmbeddingModel`, `EmbeddingRequest`, `EmbeddingUtils`          |
| Reranking and RAG     | `RerankingModel`, `RagMiddlewares`, `RagMiddlewareOptions`      |
| Images                | `ImageGenerationModel`, `GenerateImageRequest`, `GeneratedFile` |
| Request control       | `CancellationSource`, `GenerationTimeouts`, lifecycle APIs      |
| Browser stream bridge | `UIMessageChatHandlers`, `UIMessageStreamResponse`              |

The API is reactive: completed values normally use `Mono<T>` and event streams use `Flux<T>`.
Preserve `responseMessages` when continuing a conversation or tool loop, and treat model
capabilities and warnings as runtime data.

Return to the [developer documentation](../README.md).
