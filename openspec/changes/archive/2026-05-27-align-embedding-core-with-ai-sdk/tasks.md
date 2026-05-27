## 1. Public API Contracts

- [x] 1.1 Change `EmbeddingRequest.providerOptions` to namespaced provider options and add `headers`, `maxRetries`, and `maxParallelCalls`
- [x] 1.2 Document `EmbeddingRequest` fields as the Java equivalent of AI SDK embedding settings: provider options, parallel requests, retries, timeout/cancellation, and custom headers
- [x] 1.3 Add embedding response value types for usage, response metadata, warnings, and provider metadata
- [x] 1.4 Extend `EmbeddingResponse` with embeddings, usage, response metadata, warnings, and provider metadata
- [x] 1.5 Add JavaDoc examples for the advanced embedding request and response diagnostics
- [x] 1.6 Add a public cosine similarity utility with validation for null, empty, and mismatched vectors

## 2. Provider-Neutral Embedding Execution

- [x] 2.1 Validate advanced embedding request fields before provider invocation
- [x] 2.2 Apply request-level batching with `maxBatchSize` and provider maximum limits
- [x] 2.3 Apply `maxParallelCalls` while preserving input order across parallel batches
- [x] 2.4 Apply `maxRetries` per retryable batch call and avoid retrying validation or cancellation failures
- [x] 2.5 Preserve lifecycle, timeout, and cancellation callbacks for advanced embedding requests
- [x] 2.6 Aggregate embeddings, usage, warnings, response metadata, and provider metadata across batches

## 3. Provider Mapping

- [x] 3.1 Introduce provider-owned embedding invocation/mapping contracts or helpers that keep provider-specific logic out of `EmbeddingModelImpl`
- [x] 3.2 Map supported OpenAI-compatible embedding options such as dimensions through the provider namespace
- [x] 3.3 Apply request-scoped headers where the provider adapter supports them
- [x] 3.4 Emit stable warnings for unsupported provider options, headers, dimensions overrides, and missing diagnostics
- [x] 3.5 Map available Spring AI/provider embedding usage and response metadata into provider-neutral response fields

## 4. Documentation And Generated Client

- [x] 4.1 Update `dev/dev.md` with advanced embedding examples, retry/header/parallel controls, response diagnostics, and cosine similarity
- [x] 4.2 Regenerate the API client if serializable request/response fields affect OpenAPI output
- [x] 4.3 Add a console embedding test endpoint and workbench UI for manual verification

## 5. Verification

- [x] 5.1 Add API unit tests for request validation, cosine similarity, and embedding response value types
- [x] 5.2 Add service tests for batching, input ordering, max parallel calls, retry behavior, timeout, cancellation, and lifecycle callbacks
- [x] 5.3 Add provider mapping tests for provider options, request headers, warnings, usage, and response metadata
- [x] 5.4 Run `./gradlew compileJava`
- [x] 5.5 Run `./gradlew :app:test`
- [x] 5.6 Run `./gradlew generateApiClient` when OpenAPI fields change
- [x] 5.7 Run `pnpm --dir ui type-check` if generated frontend client files change
- [x] 5.8 Run `openspec validate --all --strict`
