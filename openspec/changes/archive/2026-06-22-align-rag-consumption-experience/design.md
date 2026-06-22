## Context

AI Foundation already exposes RAG middleware, source references, reranking models, UI Message streams, and frontend SDK message reducers. The next gap is not a built-in RAG product, but the caller experience when building AI SDK-style RAG flows: source references should map cleanly to UI Message parts, rerank metadata should survive source reordering, and documentation should show how to compose RAG with custom `data-*` parts without defining framework-specific RAG data names.

The current UI Message protocol supports `source-url` but not `source-document`. Non-URL sources such as Halo posts, pages, files, and chunks therefore cannot be represented as first-class source parts. The RAG reranking adapter currently returns sources in ranked order but drops rerank score and provider metadata. The docs contain useful RAG snippets, but need a caller-focused, coherent sequence that teaches SDK usage rather than describing internal state or endpoint boundaries.

## Goals / Non-Goals

**Goals:**

- Add `source-document` as a first-class UI Message part across Java API, stream mapping, transport codec, frontend SDK types, reducer, validation, persistence, and workbench display.
- Provide a default source-to-UI-message mapping: sources with URL become `source-url`, sources without URL become `source-document`.
- Preserve rerank result score and provider metadata in `RetrievedSource.metadata` when using `RerankingModelRagSourceReranker`.
- Verify generic custom `data-*` support is sufficient for caller-defined RAG status or diagnostics.
- Rewrite RAG consumer documentation as SDK usage guidance, including a complete UI Message streaming example with caller-defined `data-*`.

**Non-Goals:**

- Do not add vector store, document store, crawler, chunker, indexer, or knowledge base management.
- Do not introduce built-in RAG data part names or schemas.
- Do not add a built-in `RagLifecycle` to UI Message bridge.
- Do not add model recommendation catalogs.
- Do not change `RagMiddlewareOptions` for this scope.

## Decisions

1. **Represent non-URL sources as `source-document`.**

   `source-document` will mirror the AI SDK-style UI Message source part shape: `sourceId`, `mediaType`, `title`, optional `filename`, and optional `providerMetadata`. `mediaType` defaults to `text/plain`; `title` defaults to source title, then source id, then `Source`. Retrieved source content is not exposed by default.

2. **Keep generation source content as the existing source part.**

   Java generation content already has a provider-neutral source part. The change only affects UI Message projection and transport. `GenerationContentPart.source(...)` remains the model/result representation, and UI Message mapping chooses `source-url` or `source-document` based on URL presence.

3. **Add a generic source-to-UIMessage mapping helper.**

   Keep `SourceReferences.toSourceUrlPart(...)` for existing explicit URL use and add a generic mapping method that returns a UI Message source part. This keeps existing callers stable while making default RAG stream mapping simpler.

4. **Preserve rerank metadata without overwriting retrieval score.**

   Rerank score represents ranking confidence, while `RetrievedSource.score` may represent retrieval score. The adapter will preserve the original source score and add `rerankScore` and `rerankProviderMetadata` to metadata.

5. **Leave `data-*` naming to callers.**

   The framework will not standardize `data-retrieved-sources` or `data-rag-status`. Documentation can show example names, but tests should focus on generic `data-*` behavior and preservation.

6. **Workbench changes are display adaptations only.**

   The console workbench should render `source-document` wherever source parts are displayed. Existing workbench diagnostic data remains workbench-specific and is not promoted to a production standard.

## Risks / Trade-offs

- [Risk] Adding a new UI Message part can miss one encode/decode path. → Cover Java codec, validators, frontend reducer, persistence, and stream mapper with tests.
- [Risk] `source-document` defaults could hide useful metadata. → Preserve all display-safe metadata in `providerMetadata` and only promote `mediaType` / `filename` to top-level fields.
- [Risk] Rerank metadata keys may collide with caller metadata. → Use stable keys `rerankScore` and `rerankProviderMetadata`; do not overwrite caller keys except by documenting the reserved names.
- [Risk] Documentation examples could look like mandated RAG architecture. → Phrase examples as SDK usage patterns and caller-defined custom data parts, not framework-required standards.
