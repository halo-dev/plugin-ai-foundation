## Purpose

Define the public AI model service API exposed to consumer plugins.
## Requirements
### Requirement: AiModelService as Registry/Factory

The `api/` module SHALL expose an `AiModelService` interface acting as a Registry/Factory that resolves model names to capability-specific interfaces (`LanguageModel`, `EmbeddingModel`). `AiModelService` SHALL also be the Halo backend Extension Point used by consumer plugins to discover the AI Foundation service implementation.

#### Scenario: Service registry contract
- **WHEN** a consumer plugin declares `compileOnly 'run.halo.aifoundation:api:x.x.x'`
- **THEN** it SHALL have access to `AiModelService`, `LanguageModel`, `EmbeddingModel`, and all wrapper data types without adding Spring AI dependencies

#### Scenario: Extension Point marker contract
- **WHEN** a consumer plugin compiles against `AiModelService`
- **THEN** `AiModelService` SHALL be assignable to `org.pf4j.ExtensionPoint`

#### Scenario: ExtensionGetter service lookup
- **WHEN** a consumer plugin has a runtime dependency on the `ai-foundation` plugin
- **AND** the `ai-foundation` plugin is started and enabled
- **AND** the consumer calls `ExtensionGetter.getEnabledExtension(AiModelService.class)`
- **THEN** the system SHALL return the AI Foundation `AiModelService` implementation

#### Scenario: No static service locator required
- **WHEN** a consumer plugin needs to resolve a language or embedding model
- **THEN** the consumer SHALL obtain `AiModelService` through Halo's Extension Point lookup
- **AND** the public API SHALL NOT require `AiServices.getModelService()`

### Requirement: AiModelService Extension Point resources

The plugin SHALL declare Halo Extension Point metadata for the public AI model service and its built-in implementation.

#### Scenario: Extension Point definition exists
- **WHEN** the plugin resources are loaded
- **THEN** the system SHALL provide an `ExtensionPointDefinition` named `ai-model-service`
- **AND** the definition SHALL reference `run.halo.aifoundation.AiModelService`
- **AND** the definition type SHALL be `SINGLETON`

#### Scenario: Extension implementation definition exists
- **WHEN** the plugin resources are loaded
- **THEN** the system SHALL provide an `ExtensionDefinition` named `ai-foundation-ai-model-service`
- **AND** the definition SHALL reference `run.halo.aifoundation.service.AiModelServiceImpl`
- **AND** the definition SHALL point to the `ai-model-service` Extension Point

### Requirement: Consumer plugin dependency contract

Consumer plugins that require AI Foundation at runtime SHALL declare both a compile-time API dependency and a Halo plugin runtime dependency.

#### Scenario: Compile-only API dependency
- **WHEN** a consumer plugin uses AI Foundation Java types
- **THEN** its build SHALL declare the AI Foundation API module as `compileOnly`
- **AND** the consumer plugin artifact SHALL NOT bundle a second copy of the API classes

#### Scenario: Runtime plugin dependency
- **WHEN** a consumer plugin requires AI Foundation to invoke models
- **THEN** its `plugin.yaml` SHALL declare a `pluginDependencies` entry for `ai-foundation`
- **AND** Halo SHALL be able to start AI Foundation before the consumer plugin

### Requirement: LanguageModel interface definition

The system SHALL define a `LanguageModel` interface providing model-independent text generation and streaming text generation capabilities.

#### Scenario: Interface contract
- **WHEN** a consumer calls `aiModelService.languageModel("openai-official-gpt-4o-a7f3k")` where the argument is `AiModel.metadata.name`
- **AND** the corresponding `AiModel` exists
- **AND** the corresponding `AiModel` is enabled
- **AND** the corresponding `AiProvider` is configured and enabled
- **THEN** the system SHALL return a `Mono<LanguageModel>` that emits the `LanguageModel` instance on success

#### Scenario: Convenience text generation
- **WHEN** a consumer calls `languageModel.generateText("Hello")`
- **THEN** the system SHALL treat the prompt as a user message
- **AND** return a `Mono<GenerateTextResult>` containing generated text, finish reason, usage when available, and provider metadata when available

#### Scenario: Structured text generation
- **WHEN** a consumer calls `languageModel.generateText(request)` with a valid `GenerateTextRequest`
- **THEN** the system SHALL apply model-independent request fields consistently across supported providers
- **AND** return `GenerateTextResult` without exposing Spring AI or provider-native response types

#### Scenario: Streaming text generation
- **WHEN** a consumer calls `languageModel.streamText(request)` with a valid `GenerateTextRequest`
- **THEN** the system SHALL return a `Flux<TextStreamPart>`
- **AND** the stream SHALL use standardized part types for start, text deltas, finish, and error events

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

### Requirement: Text generation request

The system SHALL support structured text generation requests via `GenerateTextRequest`.

#### Scenario: Prompt request
- **WHEN** a consumer sends `GenerateTextRequest` with `prompt = "Hello"`
- **THEN** the system SHALL send the prompt to the provider as a user message

#### Scenario: Message history request
- **WHEN** a consumer sends `GenerateTextRequest` with `messages`
- **THEN** the system SHALL preserve message order when converting to the provider request
- **AND** it SHALL map system, user, and assistant roles to the corresponding provider message roles

#### Scenario: System instruction
- **WHEN** a consumer sends `GenerateTextRequest` with `system`
- **THEN** the system SHALL apply it as a system instruction before prompt or history messages

#### Scenario: Prompt and messages are mutually exclusive
- **WHEN** a consumer sends both `prompt` and `messages`
- **THEN** the request SHALL be rejected before invoking the provider

#### Scenario: Text generation options
- **WHEN** a consumer sends `maxOutputTokens`, `temperature`, `topP`, `topK`, `presencePenalty`, `frequencyPenalty`, or `stopSequences`
- **THEN** the system SHALL pass supported options to the underlying provider client through the model implementation

#### Scenario: Namespaced provider options
- **WHEN** a consumer sends `providerOptions = {"openai": {"logitBias": {"50256": -100}}}`
- **THEN** OpenAI-compatible provider adapters MAY parse and apply the `openai` namespace
- **AND** non-OpenAI provider adapters SHALL ignore the `openai` namespace unless explicitly documented otherwise

### Requirement: ModelMessage content parts

The system SHALL model language input messages as role-bearing messages containing content parts.

#### Scenario: Text message factory
- **WHEN** a consumer creates `ModelMessage.user("Hello")`
- **THEN** the message SHALL have role `USER`
- **AND** the content SHALL contain one text part with text `Hello`

#### Scenario: Text-only V1 invocation
- **WHEN** a request contains only text content parts
- **THEN** the system SHALL convert those parts to provider text messages

#### Scenario: Unsupported content part
- **WHEN** a request contains a non-text content part such as image, file, tool-call, or tool-result
- **THEN** the system SHALL reject the request before invoking the provider
- **AND** the error message SHALL identify the unsupported part type

### Requirement: GenerateTextResult

The system SHALL return a model-independent `GenerateTextResult` for non-streaming text generation.

#### Scenario: Generated text response
- **WHEN** a provider returns generated assistant text
- **THEN** `GenerateTextResult.text` SHALL contain the generated text
- **AND** `GenerateTextResult` SHALL include unified finish reason and raw finish reason when available

#### Scenario: Token usage reporting
- **WHEN** a provider response includes usage data
- **THEN** `GenerateTextResult.usage` SHALL include input token count, output token count, and total token count when available

#### Scenario: Unknown finish reason
- **WHEN** the provider does not expose a finish reason
- **THEN** `GenerateTextResult.finishReason` SHALL be `UNKNOWN`

### Requirement: Standardized TextStreamPart stream parts

The system SHALL emit `TextStreamPart` stream parts with standardized Halo-owned type values.

#### Scenario: Text streaming
- **WHEN** a streaming text response emits text content
- **THEN** the stream SHALL emit `start`, `text-start`, one or more `text-delta`, `text-end`, and `finish` parts in order

#### Scenario: Empty deltas are skipped
- **WHEN** the provider stream emits an empty text delta
- **THEN** the system SHALL NOT emit a `text-delta` part for the empty delta

#### Scenario: Streaming usage reporting
- **WHEN** a streaming text response completes and usage is available
- **THEN** the final `finish` part SHALL include usage with input token count, output token count, and total token count when available

#### Scenario: Error during streaming
- **WHEN** an error occurs during streaming
- **THEN** the stream SHALL emit a part with `type = "error"` and `errorText` before completing gracefully

### Requirement: Model info listing

The system SHALL expose `Mono<List<ModelInfo>> listModels()` to list all configured `AiModel` entries.

#### Scenario: List all configured models with enabled status
- **WHEN** a consumer calls `aiModelService.listModels()`
- **THEN** the system SHALL return all `AiModel` Extensions with their `name` (the `metadata.name`), `providerName`, `modelId`, `displayName`, and `enabled`

### Requirement: Provider info listing

The system SHALL expose `Mono<List<ProviderInfo>> listProviders()` to list all configured providers and their status.

#### Scenario: List all providers with last check time
- **WHEN** a consumer calls `aiModelService.listProviders()`
- **THEN** the system SHALL return all `AiProvider` Extensions with their `name`, `displayName`, `providerType`, `enabled`, `phase`, and `lastCheckedAt`

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

### Requirement: Model type validation for callable wrappers
The `AiModelService` implementation SHALL validate that a resolved `AiModel` has a compatible model type before returning a capability-specific callable wrapper.

#### Scenario: Language wrapper validates language model type
- **WHEN** a consumer calls `aiModelService.languageModel(modelName)`
- **AND** the resolved `AiModel` exists and is enabled
- **THEN** the system SHALL require the model profile to have `modelType = language`
- **AND** it SHALL emit a typed error if the model is not a language model

#### Scenario: Embedding wrapper validates embedding model type
- **WHEN** a consumer calls `aiModelService.embeddingModel(modelName)`
- **AND** the resolved `AiModel` exists and is enabled
- **THEN** the system SHALL require the model profile to have `modelType = embedding`
- **AND** it SHALL emit a typed error if the model is not an embedding model

### Requirement: Default callable wrapper resolution
The `AiModelService` API SHALL provide default-slot based wrapper resolution without requiring consumer plugins to inspect model capability profiles.

#### Scenario: Default language wrapper
- **WHEN** a consumer asks the service for the default language model wrapper
- **AND** a valid default language model slot is configured
- **THEN** the service SHALL resolve the configured `AiModel.metadata.name`
- **AND** return the same `Mono<LanguageModel>` behavior as `languageModel(modelName)`

#### Scenario: Default embedding wrapper
- **WHEN** a consumer asks the service for the default embedding model wrapper
- **AND** a valid default embedding model slot is configured
- **THEN** the service SHALL resolve the configured `AiModel.metadata.name`
- **AND** return the same `Mono<EmbeddingModel>` behavior as `embeddingModel(modelName)`

#### Scenario: Public API hides profile internals
- **WHEN** a consumer plugin uses default wrapper resolution
- **THEN** the consumer SHALL NOT be required to read or interpret `modelType`, `features`, or adapter metadata

### Requirement: Model name terminology
The system SHALL consistently treat the argument passed to `languageModel(modelName)` and `embeddingModel(modelName)` as `AiModel.metadata.name`.

#### Scenario: Distinguish model name from provider model ID
- **WHEN** documentation, errors, or Console copy reference a model service lookup key
- **THEN** they SHALL call it `modelName` or model reference
- **AND** they SHALL NOT confuse it with `AiModel.spec.modelId`, which is the provider-side model identifier
