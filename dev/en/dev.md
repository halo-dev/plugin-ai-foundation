# Halo AI Foundation SDK Core: Single-page reference

[简体中文](../zh-CN/dev.md) | English

This single-page reference provides a compact map of SDK Core. The task-oriented guides contain
the maintained examples and the [public API index](./sdk-core/api-reference.md) contains every
public Java type.

## Integration contract

1. Add `run.halo.aifoundation:api` as `compileOnly`.
2. Declare `ai-foundation` in `plugin.yaml`.
3. Resolve `AiModelService` through `ExtensionGetter`.
4. Store `AiModel.metadata.name`.
5. Keep Reactor request paths non-blocking.

## Capability map

| Capability                                                          | Guide                                                                        |
| ------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Text, messages, media, reasoning, streaming, result metadata        | [Generating text](./sdk-core/generating-text.md)                             |
| Objects, arrays, choices, partial and final validation              | [Structured output](./sdk-core/generating-structured-data.md)                |
| Server and external tools, steps, approval, repair, input deltas    | [Tools](./sdk-core/tools-and-tool-calling.md)                                |
| Embedding batches, similarity, reranking, RAG middleware, sources   | [Embeddings, reranking, and RAG](./sdk-core/embeddings-reranking-and-rag.md) |
| Text-to-image, image editing, masks, middleware, generated files    | [Image generation](./sdk-core/image-generation.md)                           |
| Middleware, lifecycle, cancellation, timeout, retry                 | [Middleware and lifecycle](./sdk-core/middleware-and-lifecycle.md)           |
| Resolution, provider, capability, media, timeout, and output errors | [Error handling](./sdk-core/error-handling.md)                               |
| UI Message validation, conversion, streaming, and persistence       | [UI Message Stream](./ui-message-stream.md)                                  |
| Exact types and grouped responsibilities                            | [Public API index](./sdk-core/api-reference.md)                              |

## Core rules

- Use either `prompt` or `messages`, with optional `system`.
- Preserve `responseMessages` for continued context and tool loops.
- Treat every `StreamTextResult` projection as one underlying request.
- Continue every assistant tool call with exactly one result, error, or approval decision.
- Treat capability data, warnings, and provider metadata as runtime data.
- Keep authorization, persistence, vector storage, file lifecycle, and business policy in the
  consumer plugin.

See the [complete plugin integration example](./plugin-integration-examples.md).
