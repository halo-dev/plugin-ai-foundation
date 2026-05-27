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
The system SHALL stream structured output as normal generated text while also exposing AI SDK-aligned structured stream views.

#### Scenario: Structured output remains text in full stream
- **WHEN** a structured stream emits generated content
- **THEN** `StreamTextResult.fullStream()` SHALL expose the structured content through normal text parts
- **AND** the stream SHALL NOT emit final parsed structured output on content parts or lifecycle parts
- **AND** the complete text SHALL be validated according to the requested output type before final result completion

#### Scenario: Partial output stream
- **WHEN** `streamText` is called with `OutputSpec.object(...)` or `OutputSpec.json()`
- **THEN** `StreamTextResult.partialOutputStream()` SHALL emit parsed partial JSON snapshots when the accumulated generated text can be parsed into a safe partial value
- **AND** partial snapshots SHALL NOT be treated as complete schema validation success

#### Scenario: Array element stream
- **WHEN** `streamText` is called with `OutputSpec.array(...)`
- **THEN** `StreamTextResult.elementStream()` SHALL emit each completed array element after it validates against the element schema
- **AND** incomplete array elements SHALL NOT be emitted

#### Scenario: Structured stream validation error
- **WHEN** a structured stream reaches completion but final output validation fails
- **THEN** `StreamTextResult.fullStream()` SHALL emit an `error` part with a safe validation message when possible
- **AND** `StreamTextResult.output()` and `StreamTextResult.result()` SHALL fail with the typed validation error

### Requirement: Complete structured stream output
The system SHALL expose complete parsed structured output for streamed generations.

#### Scenario: Complete output mono
- **WHEN** a structured stream completes successfully
- **THEN** `StreamTextResult.output()` SHALL complete with the final parsed structured output
- **AND** `StreamTextResult.result()` SHALL include the same value in `GenerateTextResult.output`

#### Scenario: No structured output requested
- **WHEN** a request omits `output` or uses `OutputSpec.text()`
- **THEN** `StreamTextResult.partialOutputStream()` SHALL complete without emitting structured values
- **AND** `StreamTextResult.elementStream()` SHALL complete without emitting structured values

#### Scenario: Object output streams JSON text
- **WHEN** a request uses object output and the model streams JSON text
- **THEN** `textStream()` MUST emit the JSON text deltas and `output()` MUST resolve the parsed validated object after completion

#### Scenario: Choice output streams answer text
- **WHEN** a request uses choice output
- **THEN** `textStream()` MUST emit the selected choice text and `output()` MUST resolve only if the final value is one of the allowed choices

#### Scenario: Partial object is incomplete
- **WHEN** a streamed partial object is missing required final schema fields
- **THEN** `partialOutputStream()` MAY emit that partial value and MUST NOT mark final validation as successful

#### Scenario: Completed element validates
- **WHEN** an array output stream completes an element that matches the element schema
- **THEN** `elementStream()` MUST emit that element

#### Scenario: Invalid completed element fails
- **WHEN** an array output stream completes an element that violates the element schema
- **THEN** `elementStream()` MUST fail with structured validation error details

### Requirement: Structured output with tool calling
The system SHALL support structured output and server-side tools in the same request.

#### Scenario: Tool steps before structured final answer
- **WHEN** a structured output request also includes tools and `stopWhen` allows continuation
- **THEN** tool-call steps SHALL execute normally
- **AND** structured output SHALL be parsed and validated from the final answer step

#### Scenario: Stop condition reached before structured output
- **WHEN** tool calling reaches the step limit before a final structured answer is produced
- **THEN** the result or stream SHALL include the existing stop-condition warning
- **AND** structured output validation SHALL fail if no valid final structured output exists
