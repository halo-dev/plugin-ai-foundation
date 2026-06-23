## Why

AI Foundation already provides language generation, streaming UI messages, embeddings, source parts, and provider-neutral model management, but it does not yet expose the runtime primitives needed for retrieval-augmented generation workflows in the same composable style as the rest of the SDK.

This change adds the missing model middleware, source reference, reranking, and retrieval augmentation contracts so consumer plugins can build RAG features on AI Foundation without depending on Spring AI types or each inventing their own references, streaming data, and request-preparation conventions.

## What Changes

- Add provider-neutral language model middleware with request transformation plus generate/stream wrapping hooks.
- Allow middleware to be attached at model level, request level, and UI message chat level without introducing global automatic middleware registration.
- Add first-class source reference and retrieved source models so generated results, streams, and UI messages can expose references consistently.
- Add a retrieval augmentation runtime built around a caller-provided retriever SPI, optional reranker, context packing, prompt injection, empty-context handling, failure policies, and lightweight lifecycle events.
- Add provider-neutral reranking model support, including Java SDK APIs, provider adapter hooks, model type/default slot support, console selection, and workbench testing.
- Add UI message chat async request preparation and RAG source/custom-data streaming conventions.
- Add documentation and examples showing how consumer plugins compose retrieval, reranking, middleware, streaming sources, and embeddings.
- **BREAKING**: Public SDK and console model contracts may change because the plugin is unreleased; callers should migrate to the new source, middleware, and reranking APIs as they land.

## Non-Goals

- Do not add a built-in VectorStore, DocumentStore, indexer, chunker, knowledge-base product, or repository lifecycle.
- Do not require retrieval to use embeddings; embeddings remain one possible implementation detail of a consumer-provided retriever.
- Do not add global automatic middleware registration in this change.
- Do not implement a full agent framework; only add the prepare/middleware hooks needed by chat and RAG workflows.
- Do not expose Spring AI RAG, advisor, vector store, or document types through the public API.

## Capabilities

### New Capabilities

- `language-model-middleware`: Provider-neutral model middleware, wrapping, and per-request middleware execution for language generation and streaming.
- `source-reference-runtime`: First-class source and retrieved source models, top-level source accessors, and source aggregation/mapping rules.
- `reranking-core`: Provider-neutral reranking model APIs, provider adapter hooks, model configuration, and workbench testing.
- `rag-runtime`: Retrieval augmentation runtime built around retriever/reranker SPIs, prompt injection, context handling, failure policies, and lifecycle events.
- `ui-message-rag-runtime`: UI message async preparation and RAG source/custom-data streaming behavior.

### Modified Capabilities

- `ai-model-service`: Add reranking model resolution beside language and embedding model resolution.
- `default-model-slots`: Add a default reranking model slot.
- `model-capability-profile`: Formalize rerank model type handling and provider capability reporting.
- `provider-type-registry`: Allow provider types to expose reranking support and construct reranking model clients.
- `console-model-management`: Support configuring and selecting reranking models in the console.
- `model-test-workbench`: Add reranking test coverage in the console workbench.
- `stream-text-result`: Add top-level source accessors derived from stream/content parts.
- `ui-message-stream`: Add request preparation and standard RAG source/custom-data stream behavior.
- `embedding-core-alignment`: Add AI SDK-style embedding convenience methods without changing advanced embedding behavior.
- `consumer-sdk-documentation`: Document middleware, sources, reranking, RAG runtime composition, and non-goals for storage/indexing.

## Impact

- `api/`: new public SDK packages/types for middleware, sources, retrieval augmentation, reranking, and UI message preparation; updates to `AiModelService`, `LanguageModel`, `GenerateTextRequest`, `GenerateTextResult`, `StreamTextResult`, and embedding interfaces.
- `app/`: provider adapter contracts, provider client cache, model resolution, reranking runtime implementation, source/result aggregation, middleware execution, and audit/lifecycle integration.
- `ui/`: provider/model forms, default model slots, generated API client usage, model selector filtering, and model test workbench changes for reranking.
- `ui/packages/sdk` and `ui/packages/ai-ui-vue`: UI message source/custom-data consumption may need new helper types or examples.
- `dev/` and `openspec/specs/`: caller documentation and capability specs must describe the new Halo-owned runtime contracts and explicitly keep storage/indexing out of scope.
