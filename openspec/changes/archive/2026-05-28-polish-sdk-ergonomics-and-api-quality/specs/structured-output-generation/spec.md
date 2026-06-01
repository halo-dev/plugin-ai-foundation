## ADDED Requirements

### Requirement: Structured Output Uses Typed Output Specs
Structured output APIs SHALL provide typed builders or factories for output specs and schemas so callers can request object, enum, array, or no-schema output without raw maps for normal cases.

#### Scenario: Caller requests object output
- **WHEN** a plugin author requests structured object output
- **THEN** the author can build the schema with SDK helpers and pass it through an output spec builder

#### Scenario: Caller requests enum output
- **WHEN** a plugin author requests one value from a fixed set
- **THEN** the SDK provides a typed way to express enum output and documents the expected result shape

### Requirement: Structured Output Examples Match Runtime Behavior
Structured output documentation and tests SHALL reflect the actual text/result behavior returned by the SDK.

#### Scenario: Final text is structured
- **WHEN** the provider returns structured JSON text
- **THEN** SDK examples treat the model text as the authoritative structured content unless an explicitly documented parsed helper is used

#### Scenario: Partial output is streamed
- **WHEN** structured output streaming is enabled
- **THEN** tests verify partial or element streams use the documented stream parts and do not inject extra final content parts beyond the protocol
