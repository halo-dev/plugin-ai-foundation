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
- **AND** return a `Mono<GenerateTextResult>` containing generated text, content parts, finish reason, usage, total usage, warnings, request metadata, response metadata, step details, and provider metadata when available

#### Scenario: Structured text generation
- **WHEN** a consumer calls `languageModel.generateText(request)` with a valid `GenerateTextRequest`
- **THEN** the system SHALL apply model-independent request fields consistently across supported providers
- **AND** return `GenerateTextResult` without exposing Spring AI or provider-native response types

#### Scenario: Streaming text generation
- **WHEN** a consumer calls `languageModel.streamText(request)` with a valid `GenerateTextRequest`
- **THEN** the system SHALL return a `StreamTextResult`
- **AND** `StreamTextResult.fullStream()` SHALL use standardized part types for message lifecycle, step lifecycle, text deltas, reasoning deltas, tool events, finish, raw diagnostic, abort, and error events
- **AND** `StreamTextResult.textStream()` SHALL expose only answer text deltas

### Requirement: Aggregated tool result fields
The system SHALL expose top-level aggregated tool calls, tool results, and tool errors on generation results.

#### Scenario: Multi-step tool aggregation
- **WHEN** text generation completes after one or more tool calls
- **THEN** `GenerateTextResult.toolCalls` SHALL include all model-requested tool calls in step order
- **AND** `GenerateTextResult.toolResults` SHALL include all successful server-side tool results in step order
- **AND** `GenerateTextResult.toolErrors` SHALL include all failed tool executions or validation errors in step order

#### Scenario: Step-level tool data remains available
- **WHEN** top-level tool aggregation is populated
- **THEN** each `GenerationStep` SHALL still expose the tool calls, tool results, and tool errors that belong to that step

### Requirement: Warning semantics
The system SHALL report unsupported, ignored, or downgraded generation settings as stable warnings.

#### Scenario: Unsupported setting warning
- **WHEN** a request includes a generation setting that the provider adapter cannot apply
- **THEN** the generation result or completed stream step SHALL include a warning with a stable code
- **AND** the warning SHALL identify the setting without leaking credentials or raw request bodies

#### Scenario: Structured output downgrade warning
- **WHEN** a provider adapter downgrades a structured output request from strict JSON Schema enforcement to weaker JSON object mode or prompt guidance
- **THEN** the generation result or completed stream step SHALL include a warning describing the downgrade

#### Scenario: Warning aggregation
- **WHEN** generation completes after multiple steps
- **THEN** top-level warnings SHALL aggregate warnings from all steps in step order

### Requirement: Structured output validation error details
The system SHALL expose typed structured output validation failures with safe debugging context.

#### Scenario: Validation error context
- **WHEN** final structured output parsing or validation fails
- **THEN** the raised structured output validation exception SHALL include output type, raw output text when available, validation path when available, step index when available, usage when available, and response metadata when available
- **AND** the public error message SHALL remain safe for logs and UI display

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

### Requirement: Text generation request

The system SHALL support structured text generation requests via `GenerateTextRequest`.

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
- **WHEN** a consumer sends `maxOutputTokens`, `temperature`, `topP`, `topK`, `presencePenalty`, `frequencyPenalty`, or `stopSequences`
- **THEN** the system SHALL pass supported options to the underlying provider client through the model implementation

#### Scenario: Tool generation options
- **WHEN** a consumer sends `tools`, `toolChoice`, or `stopWhen`
- **THEN** the system SHALL validate provider-serializable tool fields before invoking the provider
- **AND** the system SHALL perform at most one provider call when `stopWhen` is omitted

#### Scenario: Namespaced provider options
- **WHEN** a consumer sends `providerOptions = {"openai": {"logitBias": {"50256": -100}}}`
- **THEN** OpenAI-compatible provider adapters MAY parse and apply the `openai` namespace
- **AND** non-OpenAI provider adapters SHALL ignore the `openai` namespace unless explicitly documented otherwise

#### Scenario: Structured output request
- **WHEN** a consumer sends `GenerateTextRequest.output` with a structured output specification
- **THEN** the system SHALL represent the request with provider-neutral API DTOs
- **AND** the provider invocation SHALL receive a provider-neutral instruction or provider-specific response-format mapping when supported
- **AND** callers SHALL NOT need Spring AI, OpenAI, or provider-native schema classes

#### Scenario: Java caller sets stop condition
- **WHEN** a Java caller builds a text generation request with a stop condition
- **THEN** the language model service MUST apply that condition during generation

#### Scenario: Java caller sets prepare callback
- **WHEN** a Java caller builds a text generation request with a prepare-step callback
- **THEN** the language model service MUST invoke the callback before each model step

### Requirement: ModelMessage content parts

The system SHALL model language input messages as role-bearing messages containing content parts.

#### Scenario: Text message factory
- **WHEN** a consumer creates `ModelMessage.user("Hello")`
- **THEN** the message SHALL have role `USER`
- **AND** the content SHALL contain one text part with text `Hello`

#### Scenario: Text invocation
- **WHEN** a request contains text content parts
- **THEN** the system SHALL convert those parts to provider text messages

#### Scenario: Tool result message
- **WHEN** a request contains a `TOOL` role message with tool result content parts
- **THEN** the system SHALL convert those parts to provider tool result messages when the provider supports tool result messages

#### Scenario: Unsupported content part
- **WHEN** a request contains a non-text content part that is not supported by the current implementation or provider
- **THEN** the system SHALL reject the request before invoking the provider
- **AND** the error message SHALL identify the unsupported part type

### Requirement: GenerateTextResult

The system SHALL return a model-independent `GenerateTextResult` for non-streaming text generation.

#### Scenario: Generated text response
- **WHEN** a provider returns generated assistant text
- **THEN** `GenerateTextResult.text` SHALL contain the generated text
- **AND** `GenerateTextResult.content` SHALL contain a text content part for the generated text
- **AND** `GenerateTextResult` SHALL include unified finish reason and raw finish reason when available

#### Scenario: Tool call response
- **WHEN** a provider returns a tool call
- **THEN** `GenerateTextResult.content` SHALL contain a tool call content part with tool call id, tool name, and input when available
- **AND** the corresponding `GenerationStep` SHALL include the same tool call

#### Scenario: Tool result response
- **WHEN** the system executes a tool successfully
- **THEN** `GenerateTextResult.content` SHALL contain a tool result content part with tool call id, tool name, and result payload
- **AND** the corresponding `GenerationStep` SHALL include the same tool result

#### Scenario: Token usage reporting
- **WHEN** a provider response includes usage data
- **THEN** `GenerateTextResult.usage` SHALL include input token count, output token count, and total token count when available
- **AND** `GenerateTextResult.totalUsage` SHALL include aggregate input token count, output token count, and total token count across all generation steps when available

#### Scenario: Multi-step result
- **WHEN** text generation completes after multiple provider calls
- **THEN** `GenerateTextResult.steps` SHALL contain one `GenerationStep` for each provider call
- **AND** each step SHALL include its zero-based step index, generated text, content parts, finish reason, raw finish reason, usage, warnings, request metadata, response metadata, and provider metadata when available
- **AND** top-level `GenerateTextResult.usage` SHALL match the final step usage when available
- **AND** top-level `GenerateTextResult.totalUsage` SHALL aggregate usage from all steps when available

#### Scenario: Provider warnings
- **WHEN** the provider or adapter reports unsupported settings, ignored settings, or other non-fatal issues
- **THEN** `GenerateTextResult.warnings` SHALL include provider-neutral warning entries with code, message, and optional provider metadata
- **AND** generation SHALL still complete successfully when the issue is non-fatal

#### Scenario: Structured output response
- **WHEN** `generateText` completes with a structured output request
- **THEN** `GenerateTextResult.output` SHALL contain the parsed final structured value
- **AND** `GenerateTextResult.outputText` SHALL contain the raw text used to parse the output when available
- **AND** the final answer `GenerationStep` SHALL contain the same parsed structured output

#### Scenario: Provider returns sources
- **WHEN** a provider response includes source references
- **THEN** the generated result content MUST expose those sources through provider-neutral content parts

#### Scenario: Provider returns files
- **WHEN** a provider response includes generated files
- **THEN** the generated result content MUST expose those files through provider-neutral content parts

#### Scenario: Request and response metadata
- **WHEN** request or response metadata is available from the provider adapter
- **THEN** `GenerateTextResult.request` SHALL include provider-neutral request metadata such as request id or model id when available
- **AND** `GenerateTextResult.response` SHALL include provider-neutral response metadata such as response id, model id, timestamp, response messages, headers, or sanitized body when available

#### Scenario: Unknown finish reason
- **WHEN** the provider does not expose a finish reason
- **THEN** `GenerateTextResult.finishReason` SHALL be `UNKNOWN`

### Requirement: Standardized TextStreamPart stream parts

The system SHALL emit `TextStreamPart` stream parts with standardized Halo-owned type values.

#### Scenario: Text streaming
- **WHEN** a streaming text response emits text content
- **THEN** the stream SHALL emit `start`, `start-step`, `text-start`, one or more `text-delta`, `text-end`, `finish-step`, and `finish` parts in order

#### Scenario: Tool streaming
- **WHEN** a streaming response emits or completes a tool call
- **THEN** the stream SHALL emit `tool-call` with tool call id, tool name, and input when available
- **AND** when a server-side tool executes successfully, the stream SHALL emit `tool-result` with tool call id, tool name, and result payload
- **AND** when a server-side tool fails, the stream SHALL emit `tool-error` with tool call id, tool name, and safe error text

#### Scenario: Empty deltas are skipped
- **WHEN** the provider stream emits an empty text delta
- **THEN** the system SHALL NOT emit a `text-delta` part for the empty delta

#### Scenario: Streaming usage reporting
- **WHEN** a streaming text response completes and usage is available
- **THEN** each `finish-step` part SHALL include step usage with input token count, output token count, and total token count when available
- **AND** the final `finish` part SHALL include total usage with input token count, output token count, and total token count when available

#### Scenario: Step lifecycle
- **WHEN** a `streamText` invocation starts a provider call
- **THEN** the stream SHALL emit a `start-step` part with the current zero-based step index
- **AND** when the provider call completes normally, the stream SHALL emit a `finish-step` part with finish reason, raw finish reason, usage, warnings, request metadata, response metadata, and provider metadata when available
- **AND** multi-step generation SHALL emit one start-step and finish-step pair for each provider call

#### Scenario: Raw diagnostic part
- **WHEN** an adapter exposes sanitized raw diagnostic stream data
- **THEN** the stream MAY emit a `raw` part containing metadata
- **AND** the `raw` part SHALL NOT contain credentials, API keys, or unsanitized request bodies

#### Scenario: Error during streaming
- **WHEN** an error occurs during streaming
- **THEN** the stream SHALL emit a part with `type = "error"` and `errorText` before completing gracefully

#### Scenario: Structured output streaming
- **WHEN** a consumer calls `languageModel.streamText(request)` with a structured output specification
- **THEN** the stream SHALL expose the generated structured text through normal text parts
- **AND** the system SHALL validate the complete streamed text before emitting the final `finish`
- **AND** the stream SHALL NOT emit parsed structured output on content parts or lifecycle parts
- **AND** if validation fails, the stream SHALL emit an `error` part with a safe validation message and complete gracefully

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

### Requirement: Language model reasoning API
The system SHALL expose reasoning output through the public language model API without provider-native types.

#### Scenario: Generate text result exposes reasoning
- **WHEN** a consumer calls `languageModel.generateText(request)`
- **AND** the provider returns reasoning content
- **THEN** the result SHALL include reasoning parts, reasoning text, and reasoning token usage when available
- **AND** answer text SHALL remain separate from reasoning text

#### Scenario: Stream text exposes reasoning parts
- **WHEN** a consumer calls `languageModel.streamText(request)`
- **AND** the provider stream emits reasoning content
- **THEN** the returned stream SHALL emit standardized reasoning stream parts
- **AND** those parts SHALL NOT be emitted as answer text deltas

#### Scenario: Provider-independent request history
- **WHEN** a consumer sends `GenerateTextRequest.messages` containing assistant reasoning parts
- **THEN** the request SHALL remain valid only when the target provider adapter supports reasoning history conversion
- **AND** the public request SHALL NOT expose Spring AI or provider-native message types

### Requirement: Reasoning-aware model message content
The system SHALL support assistant reasoning content in model message history.

#### Scenario: Assistant reasoning history
- **WHEN** a request contains an assistant message with reasoning content parts
- **THEN** the implementation SHALL preserve the order of assistant text, reasoning, and tool call parts when converting to provider messages
- **AND** provider metadata attached to reasoning parts SHALL be available to the provider adapter

#### Scenario: Unsupported reasoning input
- **WHEN** a request contains reasoning parts for a provider that cannot accept reasoning history
- **THEN** the request SHALL be rejected before invoking the provider
- **AND** the error message SHALL identify reasoning content as unsupported

### Requirement: Reasoning usage reporting
The system SHALL expose reasoning token usage when providers report it.

#### Scenario: Final step reasoning tokens
- **WHEN** the final provider step reports reasoning tokens
- **THEN** `GenerateTextResult.usage.reasoningTokens` SHALL contain the final step count
- **AND** total usage SHALL include reasoning token counts accumulated across all steps when available

#### Scenario: Stream finish reasoning tokens
- **WHEN** a streaming provider reports reasoning tokens at step completion
- **THEN** the `finish-step` part SHALL include reasoning token usage
- **AND** the final `finish` part SHALL include aggregate reasoning token usage when available

### Requirement: Text generation metadata DTOs

The system SHALL define provider-neutral DTOs for generation content, warnings, request metadata, response metadata, and generation steps.

#### Scenario: Public DTO boundary
- **WHEN** a consumer compiles against the `api` module
- **THEN** the consumer SHALL be able to reference the text generation metadata DTOs without adding Spring AI or provider-native dependencies

#### Scenario: Extensible content part model
- **WHEN** the system returns generated output content
- **THEN** each content part SHALL include a stable `type` value
- **AND** text output SHALL be represented by a text content part
- **AND** non-text output part types SHALL be reserved for future provider support unless explicitly implemented

### Requirement: Tool definitions

The system SHALL allow callers to define request-scoped tools for language model generation without exposing Spring AI or provider-native types.

#### Scenario: Tool definition contract
- **WHEN** a consumer defines a tool
- **THEN** the tool SHALL include a unique name
- **AND** the tool MAY include a description
- **AND** the tool MAY include a JSON Schema input schema represented by provider-neutral JDK collection types
- **AND** the tool MAY include a JSON Schema output schema represented by provider-neutral JDK collection types
- **AND** the tool MAY include input examples represented by provider-neutral JDK collection types
- **AND** the tool MAY include a strict flag
- **AND** the tool MAY include a Reactor-based executor that accepts tool input and emits a result payload

#### Scenario: Tool schema validation
- **WHEN** the system receives model-produced tool input or executor-produced tool output
- **THEN** it SHALL validate the payload against the corresponding tool schema when present
- **AND** invalid payloads SHALL produce a safe `tool-error` without leaking secrets

#### Scenario: Duplicate tool names
- **WHEN** a request defines two tools with the same name
- **THEN** the request SHALL be rejected before invoking the provider

#### Scenario: Invalid tool name
- **WHEN** a request defines a tool with a blank or invalid name
- **THEN** the request SHALL be rejected before invoking the provider

### Requirement: Multi-step tool execution

The system SHALL execute server-side tools and continue generation across multiple provider calls when requested.

#### Scenario: Single-step default
- **WHEN** a request omits `stopWhen`
- **THEN** the system SHALL perform at most one provider call
- **AND** if the model returns a tool call, the system SHALL record the tool call but SHALL NOT execute another provider step unless `stopWhen` allows it

#### Scenario: Tool call with executor
- **WHEN** a provider returns a tool call whose name matches a request tool with an executor
- **AND** `stopWhen` allows another step
- **THEN** the system SHALL execute the tool
- **AND** append a tool result message to the next provider call
- **AND** continue generation until there are no executable tool calls or `stopWhen` stops the loop

#### Scenario: Tool call without executor
- **WHEN** a provider returns a tool call whose name matches a request tool without an executor
- **THEN** the system SHALL record the tool call
- **AND** the system SHALL add a warning indicating that the tool was not executed
- **AND** the system SHALL NOT start another provider step for that tool call

#### Scenario: Unknown tool call
- **WHEN** a provider returns a tool call whose name is not present in the request tools
- **THEN** the system SHALL record a tool error
- **AND** the system SHALL stop the multi-step loop

#### Scenario: Tool execution failure
- **WHEN** a tool executor fails
- **THEN** the system SHALL record a tool error with a safe error message
- **AND** the system SHALL stop the multi-step loop

#### Scenario: Provider without tool support
- **WHEN** a request includes tools but the resolved provider/model does not support tool calling
- **THEN** non-streaming generation SHALL fail before invoking the provider
- **AND** streaming generation SHALL emit an `error` part before completing gracefully

### Requirement: Streaming tool calls in LanguageModel
The `LanguageModel.streamText` API SHALL support request-scoped server-side tools without degrading to buffered non-streaming output.

#### Scenario: Tool stream remains progressive
- **WHEN** a consumer calls `languageModel.streamText(request)` with tools
- **THEN** the returned `Flux<TextStreamPart>` SHALL emit model stream parts as provider chunks arrive
- **AND** it SHALL NOT delegate to `generateText` and replay the completed result as a synthetic stream

#### Scenario: Streamed tool loop follows stop condition
- **WHEN** a streamed tool call is executable
- **AND** `stopWhen` allows continuation
- **THEN** `LanguageModel.streamText` SHALL execute the tool and start the next provider stream step
- **AND** the stream SHALL stop when there are no tool calls, a tool cannot be executed, a tool fails, or `stopWhen` stops the loop

#### Scenario: Streamed usage is aggregated across steps
- **WHEN** a tool-enabled stream completes after multiple provider steps
- **THEN** each `finish-step` part SHALL include that step usage when available
- **AND** the final `finish` part SHALL include aggregate usage across streamed steps when available

#### Scenario: Unsupported tool provider emits stream error
- **WHEN** a provider or model does not support tool calling
- **AND** a consumer calls `languageModel.streamText(request)` with tools
- **THEN** the stream SHALL emit an `error` part before completing gracefully
- **AND** the provider SHALL NOT be invoked

### Requirement: Text generation requests expose lifecycle controls
The system SHALL allow text-generation callers to configure lifecycle callbacks, timeout, cancellation, metadata, and context through provider-neutral request fields.

#### Scenario: Java caller passes lifecycle controls
- **WHEN** a Java caller builds `GenerateTextRequest` with lifecycle callbacks, timeout, cancellation token, metadata, or context
- **THEN** the language model service MUST apply those controls during `generateText` and `streamText`
- **AND** public request types MUST remain independent from Spring AI and provider SDK classes

#### Scenario: Lifecycle controls do not enter provider prompt
- **WHEN** lifecycle metadata or context is attached to a generation request
- **THEN** the system MUST expose that data to lifecycle events
- **AND** it MUST NOT convert lifecycle metadata or context into model prompt messages unless a future explicit feature requests it

### Requirement: Embedding requests expose lifecycle controls
The system SHALL allow advanced embedding calls to configure lifecycle callbacks, timeout, cancellation, metadata, and context through provider-neutral request fields.

#### Scenario: Embedding start and finish callbacks
- **WHEN** a caller invokes an embedding request with lifecycle callbacks
- **THEN** the system MUST invoke embedding start before provider invocation and embedding finish after embeddings are produced

#### Scenario: Embedding timeout and cancellation
- **WHEN** an embedding timeout expires or cancellation is requested
- **THEN** the embedding call MUST fail with the corresponding typed timeout or cancellation error

### Requirement: Lifecycle errors use typed safe exceptions
The public API SHALL expose timeout and cancellation failures as typed safe AI Foundation exceptions.

#### Scenario: Timeout exception
- **WHEN** a generation or embedding call times out
- **THEN** the raised exception MUST identify timeout scope and contain a safe message

#### Scenario: Cancellation exception
- **WHEN** a generation or embedding call is cancelled
- **THEN** the raised exception MUST identify cancellation and contain a safe message

#### Scenario: Provider error remains distinct
- **WHEN** a provider API call fails independently of timeout or cancellation
- **THEN** the system MUST preserve provider error classification rather than reporting it as cancellation

### Requirement: Type-Safe Message And Content Parts
The AI model service SDK SHALL expose type-safe construction APIs for user, system, assistant, and tool messages and their supported content parts.

#### Scenario: Caller creates a text message
- **WHEN** a plugin author creates a user message with text content
- **THEN** the SDK provides a direct typed factory or builder without requiring a raw part type string

#### Scenario: Caller attempts an invalid part shape
- **WHEN** a plugin author constructs a content part with fields that do not belong to that part kind
- **THEN** the SDK prevents the invalid shape at construction time or fails validation before provider invocation

### Requirement: Text Generation Fields Have Closed-Loop Behavior
Every supported text generation request setting SHALL have implemented behavior, validation, JavaDoc, and tests.

#### Scenario: Supported setting is passed
- **WHEN** a plugin author sets a supported text generation option
- **THEN** the implementation maps it to provider-neutral or provider-specific behavior and tests cover the mapping

#### Scenario: Unsupported setting is discovered during audit
- **WHEN** a text generation option is found to be unsupported or misleading
- **THEN** it is removed from the public SDK instead of kept for compatibility

### Requirement: Language Model Implementation Has Focused Collaborators
The language generation implementation SHALL be split into cohesive collaborators for validation, message conversion, provider options, tool orchestration, structured output, stream normalization, lifecycle events, and result assembly.

#### Scenario: Tool orchestration is changed
- **WHEN** a developer updates tool-call behavior
- **THEN** the relevant code can be changed and tested without editing unrelated message conversion or lifecycle event code

#### Scenario: Stream normalization is changed
- **WHEN** a developer updates stream part ordering or normalization
- **THEN** tests can target that behavior without depending on the full provider invocation path

### Requirement: Text generation reasoning control
The system SHALL allow callers to express request-scoped reasoning behavior through a provider-neutral `GenerateTextRequest` setting.

#### Scenario: Caller uses provider default reasoning behavior
- **WHEN** a consumer sends `GenerateTextRequest` without reasoning settings
- **THEN** the provider invocation SHALL use the provider and model default reasoning behavior
- **AND** no provider-native reasoning control SHALL be added solely by the generic language model implementation

#### Scenario: Caller disables reasoning
- **WHEN** a consumer sends `GenerateTextRequest.reasoning` with explicit disabled mode
- **THEN** providers that support disabling reasoning SHALL map the request to the provider-native non-reasoning parameter before invocation
- **AND** providers that do not support disabling reasoning SHALL reject the request before invocation with a stable error message

#### Scenario: Caller enables reasoning
- **WHEN** a consumer sends `GenerateTextRequest.reasoning` with explicit enabled mode
- **THEN** providers that support enabling reasoning SHALL map the request to the provider-native reasoning parameter before invocation
- **AND** providers that do not support enabling reasoning SHALL reject the request before invocation with a stable error message

#### Scenario: Caller requests reasoning effort
- **WHEN** a consumer sends `GenerateTextRequest.reasoning` with an effort level
- **THEN** providers that support the requested effort SHALL map it to the provider-native reasoning effort parameter
- **AND** providers that do not support that effort SHALL reject the request before invocation with a stable error message

#### Scenario: Reasoning control conflict with raw provider options
- **WHEN** a consumer sends explicit `GenerateTextRequest.reasoning`
- **AND** the selected provider namespace in `providerOptions` includes a known provider-native reasoning control key
- **THEN** the request SHALL be rejected before invocation
- **AND** the error message SHALL tell the caller to use either the typed reasoning setting or raw provider options, not both

### Requirement: Provider-specific reasoning mapping
Provider implementations SHALL own the mapping from provider-neutral reasoning settings to provider-native request parameters.

#### Scenario: DeepSeek thinking mode mapping
- **WHEN** the selected provider type is DeepSeek
- **AND** the caller disables reasoning
- **THEN** the provider invocation SHALL include DeepSeek thinking mode disabled in the provider-native request body

#### Scenario: DeepSeek reasoning enabled mapping
- **WHEN** the selected provider type is DeepSeek
- **AND** the caller enables reasoning
- **THEN** the provider invocation SHALL include DeepSeek thinking mode enabled in the provider-native request body

#### Scenario: OpenAI-compatible effort mapping
- **WHEN** the selected provider adapter supports OpenAI-compatible reasoning effort
- **AND** the caller requests a supported effort level
- **THEN** the provider invocation SHALL include the matching provider-native reasoning effort value

#### Scenario: Unsupported provider reasoning control
- **WHEN** a provider adapter has no reasoning control mapping
- **AND** the caller sends an explicit reasoning setting
- **THEN** the system SHALL reject the request before invoking the provider
