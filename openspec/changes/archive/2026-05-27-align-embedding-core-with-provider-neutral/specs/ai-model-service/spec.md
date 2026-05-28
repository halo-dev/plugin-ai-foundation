## MODIFIED Requirements

### Requirement: EmbeddingModel interface definition

The system SHALL define an `EmbeddingModel` interface providing text embedding capabilities.

#### Scenario: Interface contract
- **WHEN** a consumer calls `aiModelService.embeddingModel("openai-official-text-embedding-3-small-b2c4d")` where the argument is `AiModel.metadata.name`
- **AND** the corresponding `AiModel` exists
- **AND** the corresponding `AiModel` is enabled
- **AND** the corresponding `AiProvider` is configured and enabled
- **THEN** the system SHALL return a `Mono<EmbeddingModel>` that emits the `EmbeddingModel` instance on success

#### Scenario: Batch embedding
- **WHEN** a consumer calls `embeddingModel.embed(List.of("text1", "text2", "text3"))`
- **AND** the provider supports embedding
- **THEN** the system SHALL return a `Mono<EmbeddingResponse>` containing a list of float arrays
- **AND** if the input list exceeds `maxEmbeddingsPerCall()`, the system SHALL automatically split into batches and aggregate results in input order

#### Scenario: Query embedding
- **WHEN** a consumer calls `embeddingModel.embedQuery("what is Halo plugin?")`
- **AND** the provider supports embedding
- **THEN** the system SHALL return a `Mono<float[]>` containing a single query embedding

#### Scenario: Advanced embedding request
- **WHEN** a consumer calls `embeddingModel.embed(request)`
- **AND** `request` contains `inputs`, optional `dimensions`, optional `maxBatchSize`, optional namespaced `providerOptions`, optional `headers`, optional `maxRetries`, optional `maxParallelCalls`, optional lifecycle callbacks, optional timeout settings, and optional cancellation token
- **THEN** the system SHALL apply supported advanced options to the underlying provider request
- **AND** the API SHALL remain independent of Spring AI `EmbeddingOptions`

#### Scenario: Embedding batch limits exposed
- **WHEN** a consumer accesses `embeddingModel.maxEmbeddingsPerCall()`
- **THEN** the system SHALL return the provider-specific batch limit (e.g., 96 for OpenAI)
- **AND** `embeddingModel.supportsParallelCalls()` SHALL indicate whether parallel batch execution is supported

#### Scenario: Dimensions override for RAG-style indexing
- **WHEN** a consumer sends an `EmbeddingRequest` with `dimensions = 1024`
- **THEN** the system SHALL pass the dimensions override to providers that support it
- **AND** providers that do not support dimensions override SHALL report a stable warning or reject the request before invocation according to provider behavior

#### Scenario: Caller batch size override
- **WHEN** a consumer sends an `EmbeddingRequest` with `maxBatchSize = 36`
- **THEN** the system SHALL use that value as a caller-side batching limit in addition to any provider-imposed maximum

#### Scenario: Namespaced provider options
- **WHEN** a consumer sends `EmbeddingRequest.providerOptions = {"openai": {"dimensions": 512}}`
- **THEN** OpenAI-compatible embedding provider implementations MAY parse and apply the `openai` namespace
- **AND** other provider implementations SHALL ignore unrelated namespaces unless explicitly documented otherwise
- **AND** ignored namespaces or options SHALL be reported as warnings when the request otherwise succeeds

#### Scenario: Request headers
- **WHEN** a consumer sends `EmbeddingRequest.headers = {"X-Custom-Header": "custom-value"}`
- **THEN** provider implementations that support request-scoped headers SHALL include those headers in the provider request
- **AND** providers that cannot apply request-scoped headers SHALL report a stable warning

#### Scenario: Retry budget
- **WHEN** a consumer sends `EmbeddingRequest.maxRetries`
- **THEN** the embedding implementation SHALL use that value as the maximum retry count for retryable provider call failures
- **AND** validation failures and cancellation failures SHALL NOT be retried

#### Scenario: Parallel call limit
- **WHEN** a consumer sends `EmbeddingRequest.maxParallelCalls = 2`
- **AND** the provider supports parallel calls
- **THEN** the embedding implementation SHALL execute at most 2 provider batch calls concurrently
- **AND** returned embeddings SHALL preserve input order

#### Scenario: Embedding response metadata
- **WHEN** an embedding request completes
- **THEN** `EmbeddingResponse` SHALL include embeddings, usage, response metadata, warnings, and provider metadata when available
