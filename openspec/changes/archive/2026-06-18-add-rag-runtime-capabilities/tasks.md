## 1. Language Model Middleware

- [x] 1.1 Add public middleware context, middleware, and wrapper types for request transformation, non-streaming generation wrapping, and streaming generation wrapping.
- [x] 1.2 Add request-level middleware support to `GenerateTextRequest` while preserving existing prompt/messages/system semantics.
- [x] 1.3 Implement middleware execution order for model-level and request-level middleware.
- [x] 1.4 Add unit tests proving middleware transforms requests asynchronously and wraps `generateText` and `streamText` without duplicate provider execution.
- [x] 1.5 Document middleware ordering and no-global-registration behavior in JavaDoc.

## 2. Source Reference Runtime

- [x] 2.1 Add public `RetrievedSource`, `RetrievedContext`, and `SourceReference` models with sanitized metadata conventions.
- [x] 2.2 Add mapping helpers from retrieved sources to public source references and existing generation/UI source parts.
- [x] 2.3 Add top-level source accessors to `GenerateTextResult`, `GenerationStep`, and `StreamTextResult`.
- [x] 2.4 Update stream result aggregation so source accessors are derived from the same full stream/result state.
- [x] 2.5 Add tests for URL and non-URL source mapping, source aggregation, and no duplicate stream execution.

## 3. Reranking Core

- [x] 3.1 Add public reranking request, document, response, result item, warning, usage, metadata, and model interfaces.
- [x] 3.2 Add `AiModelService.rerankingModel()` and `AiModelService.rerankingModel(String)` with model type validation.
- [x] 3.3 Add provider type hooks, provider client cache support, model runtime factory, and unsupported-provider errors for reranking.
- [x] 3.4 Add `rerank` model type support to model capability/profile handling, provider metadata, model options, and default model slots.
- [x] 3.5 Add backend console/test endpoints for reranking using generated OpenAPI contracts.
- [x] 3.6 Add backend unit tests for reranking model resolution, provider unsupported behavior, response index preservation, cancellation/timeouts, and provider option warnings.

## 4. RAG Runtime

- [x] 4.1 Add public retriever SPI, retrieval request, RAG middleware options, context packing, prompt placement, empty-context, retrieval-failure, and rerank-failure policies.
- [x] 4.2 Implement RAG middleware on top of language model middleware and caller-provided retrievers.
- [x] 4.3 Implement default last-user-message context injection plus system and new-user-message placement options.
- [x] 4.4 Implement configurable empty-context responses without calling the language model by default.
- [x] 4.5 Implement retrieval failure and rerank failure policies with warnings for explicit fallback modes.
- [x] 4.6 Add `RagSourceReranker` plus a `RerankingModel`-backed adapter without making reranking mandatory.
- [x] 4.7 Add lightweight RAG lifecycle events for retrieval, reranking, context packing, and errors with safe default payloads.
- [x] 4.8 Add tests for retriever invocation, context packing, prompt injection, empty context, failure policies, reranking adapter behavior, and lifecycle events.

## 5. UI Message RAG Runtime

- [x] 5.1 Add async UI message chat prepare hook with access to chat request, effective UI messages, converted model messages, and request builder context.
- [x] 5.2 Allow UI message chat options to attach language model middleware for request-scoped execution.
- [x] 5.3 Add standard RAG UI custom data names and output modes for sources-only and sources-with-retrieved-data behavior.
- [x] 5.4 Update UI message stream mapping/reduction to emit and persist source references before answer text by default.
- [x] 5.5 Add tests for async prepare, RAG source streaming order, persisted assistant sources, and opt-in retrieved content data.

## 6. Console And Frontend

- [x] 6.1 Regenerate OpenAPI and TypeScript clients after backend API changes.
- [x] 6.2 Update model forms, model selectors, and default model slot UI to support reranking models using generated clients and provider metadata.
- [x] 6.3 Add reranking test mode to the model test workbench with query, candidate document input, provider options, and ranked result display.
- [x] 6.4 Add focused frontend tests or type checks for reranking selectors and workbench request construction.

## 7. Embedding Ergonomics

- [x] 7.1 Add single-value and many-value embedding convenience methods while preserving advanced `EmbeddingRequest`.
- [x] 7.2 Add tests proving convenience methods preserve input order and delegate to the same embedding runtime behavior.
- [x] 7.3 Update JavaDoc and developer docs to distinguish query embedding, document/value embedding, and advanced embedding requests.

## 8. Documentation And Validation

- [x] 8.1 Update `dev/dev.md` with middleware, source references, reranking, RAG retriever SPI, UI streaming, and non-goals for storage/indexing.
- [x] 8.2 Update UI message stream documentation with async prepare and RAG source/custom-data streaming examples.
- [x] 8.3 Add caller-first examples for direct reranking, retriever-backed RAG middleware, optional reranking, and UI message chat integration.
- [x] 8.4 Run `openspec validate add-rag-runtime-capabilities --strict`.
- [x] 8.5 Run backend tests covering API/runtime changes.
- [x] 8.6 Run frontend type checks and relevant UI tests after generated client updates.
