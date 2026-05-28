## Context

provider-neutral AI API treats embeddings as a first-class surface with `embed`, `embedMany`, token usage, provider options, parallel controls, retry controls, custom headers, abort/timeout, response information, and cosine similarity helpers. Halo AI Foundation already has simple query embedding, batch embedding, and an advanced `EmbeddingRequest`, but the advanced path currently exposes only a subset of that behavior and returns only vectors.

This change completes the already-started embedding surface instead of adding unrelated provider-neutral AI API features. The public API remains provider-neutral and Spring AI-free, while provider-specific behavior stays inside provider type implementations and service adapters.

## Goals / Non-Goals

**Goals:**

- Make `EmbeddingRequest` match the practical controls provider-neutral AI API exposes for embeddings: provider options, max parallel calls, max retries, headers, timeouts, and cancellation.
- Make `EmbeddingResponse` carry production diagnostics: embeddings, usage, response metadata, provider metadata, and warnings.
- Keep `embedQuery` and `embed(List<String>)` as convenience calls, with `embed(EmbeddingRequest)` as the full-control path.
- Preserve input order across batching and parallel execution.
- Add cosine similarity as a public SDK helper.
- Avoid provider-specific checks in generic service code except through stable provider type/adaptor contracts.

**Non-Goals:**

- No vector store, retrieval API, text splitting, or ranking pipeline.
- No embedding middleware abstraction.
- No multimodal embedding input.
- No UI workflow changes beyond generated API typing if serializable request/response fields change.
- No backward compatibility layer for the old embedding request shape.

## Decisions

### Treat provider-neutral embedding settings as a first-class API group

The advanced embedding request will expose a clear settings group matching provider-neutral AI API's Embeddings settings: `providerOptions`, `maxParallelCalls`, `maxRetries`, timeout/cancellation, and request `headers`. Halo will express abort signals through the existing Java `CancellationToken` and timeout controls instead of copying the browser `AbortSignal` type.

Alternative considered: leave these as scattered request fields without naming them settings. Rejected because the implementation checklist should mirror provider-neutral AI API's documented structure and make omissions easier to catch.

### Use namespaced provider options for embeddings

`EmbeddingRequest.providerOptions` will use `Map<String, Map<String, Object>>`, matching text generation and provider-neutral AI API's provider-specific option grouping. This prevents collisions between providers and gives provider types a stable namespace to inspect.

Alternative considered: keep `Map<String, Object>` and document provider-specific keys. Rejected because it diverges from the text generation API and makes future multi-provider options ambiguous.

### Keep provider mapping behind provider-neutral contracts

Generic embedding service code will not branch on concrete provider quirks. It will pass provider-neutral request controls to an embedding invocation helper, and provider type behavior will translate supported options into the underlying Spring AI/provider request shape. Unsupported or ignored options will produce stable warnings in the response instead of silent no-ops.

Alternative considered: detect OpenAI-compatible providers directly in `EmbeddingModelImpl`. Rejected because it repeats the vendor-specific coupling that the project has already moved away from in text generation.

### Aggregate batch response metadata explicitly

Batch embedding may require multiple provider calls. `EmbeddingResponse` will aggregate embeddings in input order, sum usage when providers expose token counts, keep response metadata for the last provider response, and keep per-batch response metadata in provider metadata when useful. Warnings will identify unsupported controls or downgraded behavior.

Alternative considered: return only the final provider response metadata. Rejected because callers need visibility into partial provider behavior when a large batch is split.

### Bound parallel execution with request-level controls

`maxParallelCalls` will limit concurrent batch provider calls when the provider supports parallel calls. When absent, the existing provider capability determines sequential versus parallel execution. A value less than one is invalid.

Alternative considered: keep a boolean provider-level `supportsParallelCalls` only. Rejected because provider-neutral AI API lets the caller tune concurrency for batch embedding workloads.

### Implement request-scoped retries in the embedding layer

`maxRetries` will control retry attempts for batch provider calls. Default behavior will follow provider-neutral AI API's shape: unset means a small retry budget, `0` disables retries. Retry handling must not retry validation failures or cancellation.

Alternative considered: rely only on provider HTTP client retries. Rejected because callers need consistent behavior across providers.

### Add a small SDK utility rather than a service call

Cosine similarity will be exposed as a public utility because it operates on vectors already returned by the model and does not require provider access.

Alternative considered: place similarity on `EmbeddingModel`. Rejected because similarity is pure math and should not require a model instance.

## Risks / Trade-offs

- [Risk] Spring AI embedding response metadata may vary by provider and version. -> Mitigation: map known common fields first, store provider-specific details under `providerMetadata`, and emit warnings when diagnostics are unavailable.
- [Risk] Provider-specific headers and retry behavior may not be supported uniformly by every Spring AI adapter. -> Mitigation: apply them where the provider implementation can support them and return warnings for unsupported controls.
- [Risk] Parallel batch execution can change result ordering if collected naively. -> Mitigation: track batch indexes and flatten results by original batch order.
- [Risk] Retrying a partially failed multi-batch request can duplicate provider work. -> Mitigation: retry per batch call and aggregate only successful completed batch results.
