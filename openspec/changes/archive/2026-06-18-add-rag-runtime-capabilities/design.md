## Context

AI Foundation currently exposes provider-neutral language generation, streaming, tool execution, structured output, embeddings, UI message streams, source URL parts, and model management. Those pieces are enough for a consumer plugin to hand-roll retrieval-augmented generation, but they do not yet form a stable runtime contract for RAG-style workflows.

The target shape follows the composable structure used by AI SDK-style runtimes: embeddings and reranking are core model capabilities; retrieval is supplied by the application; middleware prepares and wraps model calls; generated outputs expose sources; UI streams can send source references and custom data. The public contract remains Halo-owned and must not expose Spring AI RAG, advisor, vector store, or document types.

Current gaps:

| Reference capability | Current AI Foundation state | Planned action |
| --- | --- | --- |
| Embedding single and batch calls | Advanced embedding exists, but naming is less direct for single value and batch semantics | Add convenience methods while preserving advanced `EmbeddingRequest` |
| Cosine similarity | `EmbeddingUtils.cosineSimilarity` exists | Keep as-is and document for retriever implementations |
| Language model middleware | No general model/request middleware | Add transform, generate wrap, and stream wrap hooks |
| Per-request preparation | UI message handler only has synchronous request customizer | Add async UI message prepare hook; Core uses request middleware |
| Result sources | Source parts exist, but no top-level source accessors | Add `SourceReference` and top-level result/stream accessors |
| Streaming source/custom data | UI source/data chunks exist, but no RAG conventions | Add RAG output modes and standard data names |
| Reranking | Model type appears in capability planning, but no public runtime | Add `RerankingModel`, request/response types, provider hooks, console support |
| RAG orchestration | No retriever SPI or prompt injection runtime | Add `RagRetriever`, optional reranker, context packing, policies, lifecycle |
| Storage/indexing | Not present | Keep out of scope; owned by consumer plugins |

## Goals / Non-Goals

**Goals:**

- Provide a general language model middleware layer that other capabilities can reuse.
- Add first-class source models and source accessors for generated results, streams, and UI messages.
- Add provider-neutral reranking as a core model capability.
- Add a RAG runtime that composes caller-provided retrieval, optional reranking, context packing, prompt injection, source output, and failure policies.
- Add UI message async preparation and stable RAG source/custom-data streaming behavior.
- Keep the public API free of Spring AI types and preserve the distinction between provider type, provider resource name, and model resource name.

**Non-Goals:**

- Build a knowledge-base product, vector database, document store, indexer, chunker, repository reconciler, or document synchronization layer.
- Require RAG retrieval to use embeddings.
- Add global automatic middleware registration.
- Build a complete agent framework.
- Preserve compatibility with unreleased API shapes.

## Decisions

### 1. Use Language Model Middleware As The Foundation

Add a `LanguageModelMiddleware` abstraction with three hooks:

- `transformRequest`: async request transformation before the model call.
- `wrapGenerate`: wrapper for non-streaming generation.
- `wrapStream`: wrapper for streaming generation.

Rationale: RAG request augmentation, source emission, guardrails, cache, fallback, and logging all need the same extension point. A RAG-only hook would create a narrow path and force future capabilities to invent parallel wrappers.

Alternatives considered:

- Only add request transformation. Rejected because source/result wrapping and stream source emission require wrapping the actual generate/stream execution.
- Add only RAG middleware. Rejected because the underlying capability is not RAG-specific.

### 2. Support Model, Request, And UI Message Middleware

Middleware attaches in three places:

- Model-level wrapping through a helper such as `LanguageModelMiddlewares.wrap(model, middleware...)`.
- Request-level middleware on `GenerateTextRequest`.
- UI message chat-level middleware on `UIMessageChatOptions`, ultimately applied as request middleware.

Execution order is model-level middleware outside request/UI middleware, then the real model.

Rationale: Some enhancements are stable for a model, while RAG is commonly per request because repositories, permissions, and retrieval options vary by conversation. UI message handlers need the same path without making callers manually build temporary wrapped models.

Global automatic registration is intentionally excluded to avoid hidden request mutation across plugin boundaries.

### 3. Separate Retrieved Sources From Result Source References

Introduce two related models:

- `RetrievedSource`: internal retrieval/context source, including fields such as `id`, `sourceType`, `title`, `url`, `content`, `score`, `metadata`, and visibility/use flags.
- `SourceReference`: public result/UI source reference, including `id`, `sourceType`, `title`, `url`, optional score, and sanitized metadata.

Rationale: Retrieval needs content and scoring data for prompt injection, but result/UI references should not expose full retrieved content by default. Existing `GenerationContentPart.source` and `SourceUrlPart` are useful output forms, but they are too URL-oriented to be the canonical RAG source model.

### 4. Keep Retrieval As An SPI, Not A Storage Product

Add `RagRetriever` with a request that includes query, messages/request context, metadata, max result count, minimum score, and generic options. The retriever returns a `RetrievedContext`.

Do not add VectorStore, DocumentStore, Indexer, or Chunker APIs in this change.

Rationale: AI Foundation should define how retrieval results are consumed by model calls, not how every plugin stores, indexes, chunks, or fetches documents. A consumer plugin can implement the retriever with Lucene, Halo search, an external service, embeddings, SQL, or any other backend.

### 5. Inject Context Into Messages By Default

RAG middleware defaults to injecting packed context into the last user message. It also supports system and new-user-message placement. No provider-visible `ragContext` field is added to `GenerateTextRequest`.

Rationale: Providers understand messages, not a special RAG field. Middleware should transform the request into an explicit model input shape. Last-user-message placement matches common SDK-style RAG middleware behavior and keeps system instructions focused on role/policy unless the caller opts in.

### 6. Treat Empty Context And Failures Separately

Empty context means retrieval completed but returned no usable source. Default behavior is to avoid calling the language model and return a configurable empty-context response.

Retrieval failure means retriever execution failed. Default behavior is to fail the request. Callers may explicitly downgrade failures to empty context or continue without context, with warnings.

Rerank failure defaults to failure when a reranker is configured. Callers may explicitly fall back to retrieved order, with warnings.

Rationale: Empty results and broken retrieval are operationally different. Silent fallback would hide production issues and mislead users.

### 7. Emit Sources Before Text In Streams

For streaming RAG calls, source references are emitted after stream start and before generated text by default. Final results and persisted UI messages keep the same source references. Full retrieved content is not sent to the frontend unless explicitly enabled.

Rationale: Users and clients can show what material is being used while the answer streams, and stored messages keep references after refresh. Source content can be sensitive or large, so exposure must be explicit.

### 8. Add Reranking As A Core Model Capability

Add a provider-neutral `RerankingModel` with `rerank(RerankRequest)`. Add request/response types that keep original input indexes so callers can map rankings back to their own objects. Add `AiModelService.rerankingModel(...)`, provider adapter hooks, model type/default slot support, UI filtering, and workbench testing.

Rationale: Reranking is a core search relevance capability and belongs beside language and embedding models. It must not be coupled to RAG or knowledge-base storage.

RAG middleware depends on a `RagSourceReranker` abstraction. AI Foundation provides an adapter backed by `RerankingModel`, while consumer plugins can provide their own reranker services.

### 9. Add UI Message Async Preparation

Add an async prepare hook to `UIMessageChatOptions` that can inspect chat request data, converted messages, metadata, and caller context before the final model request is executed.

Core generation uses request-level middleware rather than a separate `prepareCall` field, to avoid confusion with existing per-step preparation.

Rationale: UI chat endpoints commonly need async permission checks, repository lookup, retrieval configuration, or request-scoped model options before streaming. The existing synchronous request customizer cannot support that.

### 10. Preserve Halo-Owned Naming And Documentation

Design documentation can include a parity audit against AI SDK-style capabilities. Public specs, Java APIs, user docs, and UI copy should describe Halo-owned concepts such as model middleware, source references, retrieval augmentation, and reranking rather than presenting the feature as an implementation of an external SDK.

## Risks / Trade-offs

- [Risk] Middleware ordering can become hard to reason about. -> Mitigation: define deterministic model-level then request-level order, preserve list order, and document the order with tests.
- [Risk] Source models may duplicate existing content/source parts. -> Mitigation: keep `RetrievedSource` and `SourceReference` as semantic models and map them to existing parts for transport.
- [Risk] RAG middleware may leak retrieved content to UI. -> Mitigation: default to references only; full retrieved content requires explicit output mode.
- [Risk] Reranking provider support may be sparse at first. -> Mitigation: implement the public contract, fake/test runtime, UI schema, and at least one real provider only when its endpoint semantics are confirmed.
- [Risk] Retrieval failure downgrade can mask issues. -> Mitigation: default to failure and emit warnings for every configured fallback.
- [Risk] Large contexts can exceed provider limits. -> Mitigation: include max source count and max context length in context packing, with future room for token-budget packing.
- [Risk] UIMessage async preparation may overlap with middleware. -> Mitigation: treat UI prepare as an endpoint convenience that produces or attaches request middleware; Core remains middleware-based.

## Migration Plan

1. Add middleware and source models in the public API, then route existing language model calls through the middleware executor with no middleware as a no-op.
2. Add top-level source accessors while preserving existing content and stream parts as the aggregation source.
3. Add reranking model contracts, provider hooks, model type plumbing, console support, and workbench testing.
4. Add RAG runtime APIs and tests using fake retriever/reranker implementations before integrating provider-backed reranking.
5. Add UI message async prepare and RAG source/custom-data streaming helpers.
6. Update developer documentation and examples.

Because the plugin is unreleased, no compatibility layer is required. Rollback is normal branch revert before release.

## Open Questions

- Which real provider should be the first production reranking adapter after the provider contract is in place?
- Should source reference content remain only in data chunks when explicitly exposed, or should a future non-URL source UI part be added?
- Should context packing eventually support token-count budgets when model token metadata becomes reliable enough?
