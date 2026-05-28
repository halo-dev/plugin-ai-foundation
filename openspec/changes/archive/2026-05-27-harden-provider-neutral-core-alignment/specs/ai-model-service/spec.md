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
- **THEN** the system SHALL return a `StreamTextResult`
- **AND** `StreamTextResult.fullStream()` SHALL use standardized part types for message lifecycle, step lifecycle, text deltas, reasoning deltas, tool events, finish, raw diagnostic, abort, and error events
- **AND** `StreamTextResult.textStream()` SHALL expose only answer text deltas

## ADDED Requirements

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
