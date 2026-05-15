## MODIFIED Requirements

### Requirement: LanguageModel interface definition

The system SHALL define a `LanguageModel` interface providing chat and streaming chat capabilities.

#### Scenario: Interface contract
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")` where the argument is `AiModel.metadata.name`
- **AND** the corresponding `AiModel` exists
- **AND** the corresponding `AiModel` is enabled
- **AND** the corresponding `AiProvider` is configured and enabled
- **THEN** the system SHALL return a `Mono<LanguageModel>` that emits the `LanguageModel` instance on success

#### Scenario: Synchronous chat
- **WHEN** a consumer calls `languageModel.chat("Hello")`
- **THEN** the system SHALL return a `Mono<String>` with the AI response

#### Scenario: Streaming chat with history
- **WHEN** a consumer calls `languageModel.streamChat(request)` where `request` contains a list of messages
- **AND** the corresponding provider is configured and enabled
- **THEN** the system SHALL return a `Flux<ChatChunk>` emitting content chunks
- **AND** the final chunk SHALL have `last = true`

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
- **AND** if the input list exceeds `maxEmbeddingsPerCall()`, the system SHALL automatically split into batches and aggregate results

#### Scenario: Query embedding
- **WHEN** a consumer calls `embeddingModel.embedQuery("what is Halo plugin?")`
- **AND** the provider supports embedding
- **THEN** the system SHALL return a `Mono<float[]>` containing a single query embedding

#### Scenario: Advanced embedding request
- **WHEN** a consumer calls `embeddingModel.embed(request)`
- **AND** `request` contains `inputs`, optional `dimensions`, optional `maxBatchSize`, and optional `providerOptions`
- **THEN** the system SHALL apply supported advanced options to the underlying provider request
- **AND** the API SHALL remain independent of Spring AI `EmbeddingOptions`

#### Scenario: Embedding batch limits exposed
- **WHEN** a consumer accesses `embeddingModel.maxEmbeddingsPerCall()`
- **THEN** the system SHALL return the provider-specific batch limit (e.g., 96 for OpenAI)
- **AND** `embeddingModel.supportsParallelCalls()` SHALL indicate whether parallel batch execution is supported

#### Scenario: Dimensions override for RAG-style indexing
- **WHEN** a consumer sends an `EmbeddingRequest` with `dimensions = 1024`
- **THEN** the system SHALL pass the dimensions override to providers that support it
- **AND** providers that do not support dimensions override MAY ignore it or reject it explicitly according to provider behavior

#### Scenario: Caller batch size override
- **WHEN** a consumer sends an `EmbeddingRequest` with `maxBatchSize = 36`
- **THEN** the system SHALL use that value as a caller-side batching limit in addition to any provider-imposed maximum

### Requirement: Typed exception hierarchy

The system SHALL return typed exceptions for different error conditions through the reactive error channel.

#### Scenario: Unconfigured model error
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")`
- **AND** no `AiModel` with that `metadata.name` exists
- **THEN** the system SHALL emit `ModelNotFoundException` through the `Mono<LanguageModel>` error channel

#### Scenario: Disabled provider error
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")`
- **AND** the `AiModel` exists but the parent `AiProvider` is disabled
- **THEN** the system SHALL emit `ProviderDisabledException` through the `Mono<LanguageModel>` error channel

#### Scenario: Disabled model error
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")` or `aiModelService.embeddingModel("openai-official-text-embedding-3-small-b2c4d")`
- **AND** the `AiModel` exists but its `spec.enabled` is `false`
- **AND** the parent `AiProvider` is enabled
- **THEN** the system SHALL emit `ModelDisabledException` through the reactive error channel

#### Scenario: Provider API error
- **WHEN** a provider API returns an HTTP error (e.g., 401 Unauthorized)
- **THEN** the system SHALL emit `ProviderApiException` with `statusCode` and `providerType` fields set through the reactive error channel
