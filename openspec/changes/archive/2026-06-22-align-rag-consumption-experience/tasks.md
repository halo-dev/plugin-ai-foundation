## 1. UI Message Document Source Protocol

- [x] 1.1 Add Java UI Message part and chunk support for `source-document`.
- [x] 1.2 Validate `source-document` fields: required `sourceId`, `mediaType`, and `title`; optional `filename` and `providerMetadata`.
- [x] 1.3 Update UI Message transport encoding and decoding to preserve `source-document` parts.
- [x] 1.4 Add backend tests for `source-document` part creation, validation, and transport round trips.

## 2. Source Reference Mapping

- [x] 2.1 Add a reusable mapping path from runtime source references to UI Message source parts.
- [x] 2.2 Map sources with non-empty URLs to `source-url` and sources without URLs to `source-document`.
- [x] 2.3 Normalize document source metadata by promoting `mediaType` and `filename` while preserving remaining metadata in `providerMetadata`.
- [x] 2.4 Add tests covering URL mapping, document mapping, defaults, and source content non-exposure.

## 3. Rerank Metadata Preservation

- [x] 3.1 Enhance the provider-backed RAG source reranker to preserve rerank score and provider metadata on the returned source metadata.
- [x] 3.2 Keep the original retrieval `score` unchanged when rerank metadata is added.
- [x] 3.3 Add tests for reranked source ordering, metadata preservation, and original score preservation.

## 4. Frontend SDK Support

- [x] 4.1 Add `source-document` types to the frontend UI Message package.
- [x] 4.2 Update frontend stream parsing and message reducers to preserve `source-document` parts.
- [x] 4.3 Keep custom `data-*` parts generic and caller-defined without adding built-in RAG data part names.
- [x] 4.4 Add frontend tests or type checks for `source-document` and caller-defined `data-*` parts.

## 5. RAG Workbench Display

- [x] 5.1 Render `source-document` parts in the test workbench message source list.
- [x] 5.2 Keep URL sources rendered through the existing URL source path.
- [x] 5.3 Verify workbench RAG diagnostics and error display still use the existing UI Message stream path.

## 6. Caller Documentation

- [x] 6.1 Update RAG SDK documentation to teach retrieval, optional rerank, prompt assembly, generation, and source emission as caller-composed SDK usage.
- [x] 6.2 Add a non-streaming RAG example that emits final answer text and mapped sources.
- [x] 6.3 Add a UI Message streaming RAG endpoint example using `source-url`, `source-document`, and caller-defined custom `data-*` progress parts.
- [x] 6.4 Keep documentation phrasing caller-facing and avoid describing internal implementation state or console endpoint boundaries.

## 7. Validation

- [x] 7.1 Run `openspec validate align-rag-consumption-experience --strict`.
- [x] 7.2 Run focused backend tests for UI Message source mapping and rerank metadata.
- [x] 7.3 Run frontend type check or focused UI Message package tests.
- [x] 7.4 Run the broad project validation gate appropriate for the final implementation.
