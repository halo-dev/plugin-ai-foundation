## MODIFIED Requirements

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

## ADDED Requirements

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
