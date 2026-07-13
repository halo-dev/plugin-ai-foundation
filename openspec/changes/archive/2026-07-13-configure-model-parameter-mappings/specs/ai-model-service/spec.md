## MODIFIED Requirements

### Requirement: EmbeddingModel interface definition

The system SHALL define an `EmbeddingModel` interface providing text embedding capabilities through provider-neutral typed requests.

#### Scenario: Interface contract
- **WHEN** a consumer calls `aiModelService.embeddingModel(modelName)` with an enabled embedding `AiModel.metadata.name`
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
- **AND** `request` contains inputs and optional dimensions, max batch size, headers, retries, parallelism, lifecycle, timeout, cancellation, metadata, or context controls
- **THEN** the system SHALL apply supported typed controls to the runtime and effective parameter mapping
- **AND** the public request SHALL NOT expose Spring AI options or caller-writable provider-native option maps

#### Scenario: Embedding batch limits exposed
- **WHEN** a consumer accesses `embeddingModel.maxEmbeddingsPerCall()`
- **THEN** the system SHALL return the provider-specific batch limit
- **AND** `embeddingModel.supportsParallelCalls()` SHALL indicate whether parallel batch execution is supported

#### Scenario: Dimensions override for RAG-style indexing
- **WHEN** a consumer sends an `EmbeddingRequest` with `dimensions = 1024`
- **THEN** the system SHALL translate the value through the effective dimensions mapping
- **AND** an unsupported effective mapping SHALL omit dimensions and produce a stable warning

#### Scenario: Caller batch size override
- **WHEN** a consumer sends an `EmbeddingRequest` with `maxBatchSize = 36`
- **THEN** the system SHALL use that value as a caller-side batching limit in addition to any provider-imposed maximum

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

### Requirement: Text generation request

The system SHALL support structured, provider-neutral text generation requests via `GenerateTextRequest`.

#### Scenario: Prompt request
- **WHEN** a consumer sends `GenerateTextRequest` with `prompt = "Hello"`
- **THEN** the system SHALL send the prompt to the provider as a user message

#### Scenario: Message history request
- **WHEN** a consumer sends `GenerateTextRequest` with `messages`
- **THEN** the system SHALL preserve message order when converting to the provider request
- **AND** it SHALL map system, user, assistant, and supported tool roles to the corresponding provider message roles

#### Scenario: System instruction
- **WHEN** a consumer sends `GenerateTextRequest` with `system`
- **THEN** the system SHALL apply it as a system instruction before prompt or history messages

#### Scenario: Prompt and messages are mutually exclusive
- **WHEN** a consumer sends both `prompt` and `messages`
- **THEN** the request SHALL be rejected before invoking the provider

#### Scenario: Text generation options
- **WHEN** a consumer sends max output tokens, temperature, topP, topK, minP, presence penalty, frequency penalty, repetition penalty, stop sequences, seed, logprobs, top logprobs, or parallel tool-call settings
- **THEN** the system SHALL translate supplied provider parameters through the effective administrator mapping
- **AND** it SHALL keep retry, header, timeout, cancellation, metadata, and context controls in their existing runtime layers

#### Scenario: Top log probabilities imply log probabilities
- **WHEN** a consumer sets `topLogprobs` without setting `logprobs`
- **THEN** the system SHALL apply `logprobs = true`

#### Scenario: Conflicting log probability settings
- **WHEN** a consumer sets `topLogprobs` and explicitly sets `logprobs = false`
- **THEN** the system SHALL reject the request before provider invocation

#### Scenario: Tool generation options
- **WHEN** a consumer sends tools, tool choice, parallel tool calls, or a stop condition
- **THEN** the system SHALL validate provider-serializable tool fields before invoking the provider
- **AND** the system SHALL perform at most one provider call when the stop condition is omitted

#### Scenario: Structured output request
- **WHEN** a consumer sends `GenerateTextRequest.output` with a structured output specification
- **THEN** the system SHALL represent the request with provider-neutral API DTOs
- **AND** the provider invocation SHALL receive an adapter-owned response-format mapping when supported
- **AND** callers SHALL NOT need Spring AI, OpenAI, provider-native schema classes, or provider-native option maps

#### Scenario: Java caller sets stop condition
- **WHEN** a Java caller builds a text generation request with a stop condition
- **THEN** the language model service MUST apply that condition during generation

#### Scenario: Java caller sets prepare callback
- **WHEN** a Java caller builds a text generation request with a prepare-step callback
- **THEN** the language model service MUST invoke the callback before each model step

### Requirement: Text generation reasoning control
The system SHALL allow callers to express request-scoped reasoning behavior through provider-neutral default, enabled, disabled, low, medium, and high settings.

#### Scenario: Caller uses provider default reasoning behavior
- **WHEN** a consumer sends `GenerateTextRequest` without explicit reasoning settings
- **THEN** the provider invocation SHALL use provider and model default reasoning behavior
- **AND** no native reasoning control SHALL be added solely by the generic runtime

#### Scenario: Caller disables reasoning
- **WHEN** a consumer sends explicit disabled reasoning
- **THEN** the runtime SHALL apply the effective reasoning template when it supports disabled mode
- **AND** an unsupported effective mapping SHALL omit the control and return a stable warning

#### Scenario: Caller enables reasoning
- **WHEN** a consumer sends explicit enabled reasoning
- **THEN** the runtime SHALL apply the effective reasoning template when it supports enabled mode
- **AND** an unsupported effective mapping SHALL omit the control and return a stable warning

#### Scenario: Caller requests reasoning effort
- **WHEN** a consumer requests low, medium, or high reasoning effort
- **THEN** the runtime SHALL apply the administrator-configured field and typed value for that level
- **AND** an unsupported effort SHALL be omitted with a stable warning

### Requirement: Provider-specific reasoning mapping
The effective administrator mapping and selected adapter SHALL jointly own translation from provider-neutral reasoning settings to provider-native parameters.

#### Scenario: Boolean thinking mapping
- **WHEN** the effective template uses a boolean thinking switch
- **AND** the caller enables or disables reasoning
- **THEN** the provider request SHALL include the corresponding boolean value

#### Scenario: Object thinking mapping
- **WHEN** the effective template uses an enabled or disabled thinking object
- **THEN** the provider request SHALL contain the template-owned object shape

#### Scenario: Effort mapping
- **WHEN** the effective template supports effort levels
- **AND** the caller requests a supported level
- **THEN** the provider request SHALL include the matching native effort value

#### Scenario: Per-intent field and value mapping
- **WHEN** the effective mapping configures different native fields or scalar values for enabled, disabled, low, medium, or high
- **THEN** the selected caller intent SHALL emit only its corresponding native field and typed value

#### Scenario: Unsupported provider reasoning control
- **WHEN** the effective reasoning mapping cannot represent an explicit caller setting
- **THEN** the system SHALL omit the native reasoning control
- **AND** it SHALL report a stable unsupported-parameter warning

### Requirement: Settings documentation covers supported request fields
Consumer documentation SHALL explain the typed language-model request settings, administrator mapping boundary, and provider-support behavior.

#### Scenario: Common settings are documented
- **WHEN** a plugin author reads the settings section
- **THEN** the guide SHALL cover max output tokens, temperature, topP, topK, minP, presence penalty, frequency penalty, repetition penalty, stop sequences, seed, logprobs, top logprobs, parallel tool calls, retries, headers, timeout, cancellation, and reasoning options

#### Scenario: Settings depend on mapping support
- **WHEN** a setting's effective mapping is unsupported
- **THEN** the guide SHALL explain that the runtime omits the parameter and returns a warning
- **AND** the guide SHALL NOT direct callers to provider-native option maps

## ADDED Requirements

### Requirement: Public request APIs exclude caller-native option maps
Public model request, output, prepared-step, and lifecycle types SHALL NOT expose caller-writable `providerOptions`.

#### Scenario: Consumer compiles against request APIs
- **WHEN** a consumer inspects text, embedding, reranking, image, output, or step request builders
- **THEN** no `providerOptions` field or builder method SHALL be present

### Requirement: Provider-owned message state uses metadata terminology
Opaque provider state required for reasoning and tool continuation SHALL be represented as `providerMetadata`, not request `providerOptions`.

#### Scenario: Reasoning continuation state is retained
- **WHEN** a provider returns opaque reasoning state required by a later request
- **THEN** the model message part SHALL retain that state under `providerMetadata`
- **AND** normalized reasoning text SHALL remain available through typed reasoning fields
