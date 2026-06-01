## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: ChatRequest with provider options

The system no longer exposes `ChatRequest` as the public language-model request contract. Structured language calls use `GenerateTextRequest` instead.

### Requirement: Standardized ChatChunk stream parts

The system no longer exposes `ChatChunk` as the public language-model streaming contract. Streaming language calls use `TextStreamPart` instead.
