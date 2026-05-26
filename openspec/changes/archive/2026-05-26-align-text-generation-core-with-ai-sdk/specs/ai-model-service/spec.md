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
- **AND** return a `Mono<GenerateTextResult>` containing generated text, content parts, finish reason, usage, total usage, warnings, request metadata, response metadata, step details, and provider metadata when available

#### Scenario: Structured text generation
- **WHEN** a consumer calls `languageModel.generateText(request)` with a valid `GenerateTextRequest`
- **THEN** the system SHALL apply model-independent request fields consistently across supported providers
- **AND** return `GenerateTextResult` without exposing Spring AI or provider-native response types

#### Scenario: Streaming text generation
- **WHEN** a consumer calls `languageModel.streamText(request)` with a valid `GenerateTextRequest`
- **THEN** the system SHALL return a `Flux<TextStreamPart>`
- **AND** the stream SHALL use standardized part types for message lifecycle, step lifecycle, text deltas, finish, raw diagnostic, and error events

### Requirement: GenerateTextResult

The system SHALL return a model-independent `GenerateTextResult` for non-streaming text generation.

#### Scenario: Generated text response
- **WHEN** a provider returns generated assistant text
- **THEN** `GenerateTextResult.text` SHALL contain the generated text
- **AND** `GenerateTextResult.content` SHALL contain a text content part for the generated text
- **AND** `GenerateTextResult` SHALL include unified finish reason and raw finish reason when available

#### Scenario: Token usage reporting
- **WHEN** a provider response includes usage data
- **THEN** `GenerateTextResult.usage` SHALL include input token count, output token count, and total token count when available
- **AND** `GenerateTextResult.totalUsage` SHALL include aggregate input token count, output token count, and total token count across all generation steps when available

#### Scenario: Single-step result
- **WHEN** text generation completes with one provider call
- **THEN** `GenerateTextResult.steps` SHALL contain one `GenerationStep`
- **AND** the step SHALL include step index `0`, generated text, content parts, finish reason, raw finish reason, usage, warnings, request metadata, response metadata, and provider metadata when available
- **AND** top-level `GenerateTextResult.usage` SHALL match the final step usage when available
- **AND** top-level `GenerateTextResult.totalUsage` SHALL match the final step usage when there is only one step

#### Scenario: Provider warnings
- **WHEN** the provider or adapter reports unsupported settings, ignored settings, or other non-fatal issues
- **THEN** `GenerateTextResult.warnings` SHALL include provider-neutral warning entries with code, message, and optional provider metadata
- **AND** generation SHALL still complete successfully when the issue is non-fatal

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

#### Scenario: Empty deltas are skipped
- **WHEN** the provider stream emits an empty text delta
- **THEN** the system SHALL NOT emit a `text-delta` part for the empty delta

#### Scenario: Streaming usage reporting
- **WHEN** a streaming text response completes and usage is available
- **THEN** the final `finish-step` part SHALL include step usage with input token count, output token count, and total token count when available
- **AND** the final `finish` part SHALL include total usage with input token count, output token count, and total token count when available

#### Scenario: Step lifecycle
- **WHEN** a `streamText` invocation starts a provider call
- **THEN** the stream SHALL emit a `start-step` part with step index `0`
- **AND** when the provider call completes normally, the stream SHALL emit a `finish-step` part with finish reason, raw finish reason, usage, warnings, request metadata, response metadata, and provider metadata when available

#### Scenario: Raw diagnostic part
- **WHEN** an adapter exposes sanitized raw diagnostic stream data
- **THEN** the stream MAY emit a `raw` part containing metadata
- **AND** the `raw` part SHALL NOT contain credentials, API keys, or unsanitized request bodies

#### Scenario: Error during streaming
- **WHEN** an error occurs during streaming
- **THEN** the stream SHALL emit a part with `type = "error"` and `errorText` before completing gracefully

## ADDED Requirements

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
