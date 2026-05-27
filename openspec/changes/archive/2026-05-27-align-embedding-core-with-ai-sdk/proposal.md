## Why

The current embedding API is usable, but it is still much thinner than the text generation surface and AI SDK Core's embedding contract. It returns vectors but lacks request-scoped diagnostics, retry/header controls, meaningful provider option mapping, response metadata, and utility behavior that callers need for production RAG and similarity workflows.

## What Changes

- Align embedding calls with AI SDK Core's `embed` and `embedMany` behavior while preserving Halo's Java API shape:
  - single-value embedding via `embedQuery`
  - batch embedding via `embed(List<String>)`
  - full-control embedding via `embed(EmbeddingRequest)`
- **BREAKING**: Change embedding `providerOptions` to the same namespaced shape used by text generation, then map supported provider-specific options through provider implementations without exposing Spring AI types.
- Add an explicit embedding settings surface aligned with AI SDK Core's Embeddings `Settings` section:
  - provider options for provider-specific embedding parameters such as dimensions
  - `maxParallelCalls` for batch embedding concurrency
  - `maxRetries` for retryable provider failures
  - timeout/cancellation through the existing Halo lifecycle controls
  - request-scoped `headers`
- Extend `EmbeddingResponse` with usage, response metadata, provider metadata, and warnings.
- Add an embedding utility for cosine similarity.
- Keep lifecycle, timeout, and cancellation behavior for advanced embedding requests aligned with the generation lifecycle controls already introduced.
- Document the advanced embedding API, supported controls, response diagnostics, and cosine similarity helper in `dev/dev.md`.
- Add a console embedding test workflow so administrators can manually verify embedding settings, diagnostics, vector dimensions, and similarity from the backend UI.

Non-goals:

- No vector store, retrieval, ranking pipeline, or text chunking API.
- No embedding middleware surface in this change.
- No UI workflow beyond generated API/client updates caused by public serializable fields.
- No multimodal embedding input; this change remains text-only.
- No compatibility shim for older request or response shapes because the plugin is unreleased.

## Capabilities

### New Capabilities

- `embedding-core-alignment`: AI SDK-aligned embedding request/response semantics, provider options, retry/header controls, parallel controls, diagnostics, and utility behavior.

### Modified Capabilities

- `ai-model-service`: EmbeddingModel contracts gain richer request controls, usage/response/provider metadata, warnings, and utility expectations.
- `provider-type-registry`: Provider embedding implementations must map supported embedding options, request headers, retry behavior, and diagnostics consistently through the provider type system.

## Impact

- `api/`: `EmbeddingRequest`, `EmbeddingResponse`, embedding metadata/warning/value types, and an embedding utility helper.
- `app/`: `EmbeddingModelImpl`, provider option mapping, header/retry handling, response metadata extraction, warning emission, and embedding tests.
- `ui/`: regenerated API client if public serializable fields change; no hand-written API paths.
- Console test workbench: add an embedding mode for enabled embedding models.
- `dev/dev.md`: updated examples for advanced embedding requests, response diagnostics, cancellation/timeouts, and cosine similarity.
