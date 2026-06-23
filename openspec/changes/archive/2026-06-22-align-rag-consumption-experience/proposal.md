## Why

AI Foundation already provides caller-composed RAG middleware, source references, reranking, and UI Message streaming, but the consumer-facing experience is not yet fully aligned with the UI Message source and custom data model expected by AI SDK-style applications. Callers should be able to build RAG flows with first-class source parts, preserved rerank metadata, and clear SDK examples without adopting a built-in knowledge base or fixed RAG data contract.

## What Changes

- Add first-class `source-document` UI Message support alongside the existing `source-url` part.
- Provide default source mapping from `SourceReference` / retrieved sources to either `source-url` or `source-document` without exposing retrieved content by default.
- Preserve rerank score and provider metadata when `RerankingModelRagSourceReranker` reorders retrieved sources.
- Verify and document generic user-defined `data-*` parts for RAG status or diagnostics without introducing built-in RAG data part names.
- Update consumer documentation to teach SDK-based RAG composition, source rendering, custom data parts, and UI Message streaming in caller-focused terms.
- Adapt the console workbench only where needed to display `source-document`; do not add new RAG product features.

Non-goals:

- Do not add a vector store, document store, crawler, chunker, indexer, or knowledge base management UI.
- Do not introduce framework-standard RAG data part names such as `data-retrieved-sources` or `data-rag-status`.
- Do not add a built-in `RagLifecycle` to UI Message bridge; callers can compose lifecycle events with custom `data-*` parts.
- Do not add model recommendation catalogs or hardcoded rerank model recommendations.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ui-message-stream`: Add `source-document` as a first-class UI Message stream and persistence part.
- `source-reference-runtime`: Map source references to URL or document UI Message parts using display-safe metadata.
- `rag-runtime`: Preserve rerank result metadata on retrieved sources when using the provider-neutral reranking adapter.
- `consumer-sdk-documentation`: Teach caller-focused RAG composition with sources, custom data parts, and UI Message streams.
- `ai-ui-vue-package`: Support `source-document` in the frontend SDK types, reducer, validation, and persistence behavior.
- `model-test-workbench`: Display `source-document` parts when model or RAG streams contain document sources.

## Impact

- Java API/UI Message types and codec for document source parts.
- Source reference mapping helpers and UI Message stream mapper.
- RAG reranking adapter metadata preservation.
- Frontend SDK UI Message part types, reducer, validation, persistence, and tests.
- Console model test workbench source rendering.
- Consumer developer documentation and OpenSpec specs.
