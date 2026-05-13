## MODIFIED Requirements

### Requirement: AiModelService as Registry/Factory

The `api/` module SHALL expose an `AiModelService` interface acting as a Registry/Factory that resolves model names to capability-specific interfaces (`LanguageModel`, `EmbeddingModel`).

#### Scenario: Service registry contract
- **WHEN** a consumer plugin declares `compileOnly 'run.halo.aifoundation:api:x.x.x'`
- **THEN** it SHALL have access to `AiModelService`, `LanguageModel`, `EmbeddingModel`, and all wrapper data types without adding Spring AI dependencies

### Requirement: LanguageModel interface definition

The system SHALL define a `LanguageModel` interface providing chat and streaming chat capabilities.

#### Scenario: Interface contract
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")` where the argument is `AiModel.metadata.name`
- **AND** the corresponding `AiModel` exists
- **AND** the corresponding `AiProvider` is configured and enabled
- **THEN** the system SHALL return a `LanguageModel` instance

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
- **AND** the corresponding `AiProvider` is configured and enabled
- **THEN** the system SHALL return an `EmbeddingModel` instance

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

### Requirement: ChatRequest with provider options

The system SHALL support structured chat with temperature, maxTokens, and provider-specific options via `ChatRequest`.

#### Scenario: Chat with custom temperature
- **WHEN** a consumer sends a `ChatRequest` with `temperature = 0.5` and `maxTokens = 100`
- **THEN** the system SHALL pass these options to the underlying provider client

#### Scenario: Provider options pass-through
- **WHEN** a consumer sends a `ChatRequest` with `providerOptions = {"openai": {"logitBias": {"50256": -100}}}`
- **THEN** the OpenAI provider adapter SHALL parse and apply the provider-specific options
- **AND** non-OpenAI provider adapters SHALL ignore the "openai" namespace

### Requirement: Standardized ChatChunk stream parts

The system SHALL emit `ChatChunk` stream parts with standardized fields.

#### Scenario: Text streaming
- **WHEN** a streaming chat response emits text content
- **THEN** each chunk SHALL have `type = TEXT` and a `content` delta
- **AND** the final chunk SHALL have `type = FINISH`, `last = true`, and `finishReason = "stop"`

#### Scenario: Token usage reporting
- **WHEN** a streaming chat completes
- **THEN** the final `FINISH` chunk SHALL include `usage` with `promptTokens` and `completionTokens`

#### Scenario: Error during streaming
- **WHEN** an error occurs during streaming (e.g., API key invalid)
- **THEN** the stream SHALL emit a chunk with `type = ERROR` before terminating

### Requirement: Model info listing

The system SHALL expose `Mono<List<ModelInfo>> listModels()` to list all configured `AiModel` entries.

#### Scenario: List all configured models
- **WHEN** a consumer calls `aiModelService.listModels()`
- **THEN** the system SHALL return all `AiModel` Extensions with their `name` (the `metadata.name`), `providerName`, `modelId`, and `displayName`

### Requirement: Provider info listing

The system SHALL expose `Mono<List<ProviderInfo>> listProviders()` to list all configured providers and their status.

#### Scenario: List all providers
- **WHEN** a consumer calls `aiModelService.listProviders()`
- **THEN** the system SHALL return all `AiProvider` Extensions with their `providerType`, `displayName`, `enabled`, and `status.phase`

### Requirement: Typed exception hierarchy

The system SHALL return typed exceptions for different error conditions.

#### Scenario: Unconfigured model error
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")`
- **AND** no `AiModel` with that `metadata.name` exists
- **THEN** the system SHALL throw `ModelNotFoundException` with a descriptive message

#### Scenario: Disabled provider error
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")`
- **AND** the `AiModel` exists but the parent `AiProvider` is disabled
- **THEN** the system SHALL throw `ProviderDisabledException` with a descriptive message

#### Scenario: Provider API error
- **WHEN** a provider API returns an HTTP error (e.g., 401 Unauthorized)
- **THEN** the system SHALL throw `ProviderApiException` with `statusCode` and `providerType` fields set

## REMOVED Requirements

### Requirement: Invalid model reference format
**Reason**: The composite `providerName/modelId` format and its `/` separator validation are no longer used. Model references are now `metadata.name` strings with no special format.
**Migration**: Use `AiModel.metadata.name` directly as the model reference. No format parsing is needed.
