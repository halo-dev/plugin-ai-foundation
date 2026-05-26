## Purpose

Define provider-neutral structured output generation semantics for language model calls.

## Requirements

### Requirement: Structured output request
The system SHALL allow callers to request provider-neutral structured output from language model generation.

#### Scenario: Object output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `object` and a JSON Schema object
- **THEN** the request SHALL be valid when the schema itself is a JSON object
- **AND** the provider invocation SHALL receive a provider-neutral instruction or provider-specific response-format mapping when supported

#### Scenario: Array output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `array` and an element schema
- **THEN** the final structured output SHALL be a JSON array
- **AND** each completed element SHALL be validated against the element schema when validation is enabled

#### Scenario: Choice output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `choice` and a list of allowed values
- **THEN** the final structured output SHALL be one of the allowed values
- **AND** generation SHALL fail with a validation error if the final output is not an allowed value

#### Scenario: JSON output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `json`
- **THEN** the final structured output SHALL be parsed as JSON
- **AND** no object schema SHALL be required

#### Scenario: Text output remains default
- **WHEN** a request omits `output`
- **THEN** generation SHALL behave as plain text generation
- **AND** no structured output validation SHALL be applied

### Requirement: Structured output result
The system SHALL expose final structured output on generation results without requiring callers to parse answer text manually.

#### Scenario: GenerateTextResult includes final output
- **WHEN** `generateText` completes with a structured output request
- **THEN** `GenerateTextResult.output` SHALL contain the parsed final structured value
- **AND** `GenerateTextResult.outputText` SHALL contain the raw text used to parse the output when available

#### Scenario: GenerationStep includes final step output
- **WHEN** a generation step is the final answer step for a structured output request
- **THEN** that `GenerationStep` SHALL contain the parsed structured output
- **AND** earlier tool-call steps SHALL NOT be required to contain structured output

#### Scenario: Invalid final output
- **WHEN** the provider returns text that cannot be parsed or validated for the requested output type
- **THEN** `generateText` SHALL fail with a typed structured output validation error
- **AND** the error SHALL include a safe explanation without leaking provider credentials or raw secrets

### Requirement: Structured output streaming
The system SHALL stream structured output as normal generated text and validate the complete text at the end.

#### Scenario: Structured output remains text in stream
- **WHEN** a structured stream completes successfully
- **THEN** the stream SHALL expose the structured content through normal text parts
- **AND** the stream SHALL NOT emit parsed structured output on content parts or lifecycle parts
- **AND** the complete text SHALL be validated according to the requested output type before `finish`

#### Scenario: Structured stream validation error
- **WHEN** a structured stream reaches completion but final output validation fails
- **THEN** the stream SHALL emit an `error` part with a safe validation message
- **AND** the stream SHALL complete gracefully

### Requirement: Structured output with tool calling
The system SHALL support structured output and server-side tools in the same request.

#### Scenario: Tool steps before structured final answer
- **WHEN** a structured output request also includes tools and `maxSteps` allows continuation
- **THEN** tool-call steps SHALL execute normally
- **AND** structured output SHALL be parsed and validated from the final answer step

#### Scenario: Max steps reached before structured output
- **WHEN** tool calling reaches `maxSteps` before a final structured answer is produced
- **THEN** the result or stream SHALL include the existing max-step warning
- **AND** structured output validation SHALL fail if no valid final structured output exists
