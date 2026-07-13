## ADDED Requirements

### Requirement: Structured output excludes caller-native options
Structured output requests SHALL rely on typed `OutputSpec` fields and adapter-owned response-format templates.

#### Scenario: Caller constructs output specification
- **WHEN** a caller builds object, array, choice, or JSON output
- **THEN** `OutputSpec` SHALL NOT expose `providerOptions`
- **AND** the adapter SHALL select the supported native response format internally

#### Scenario: Native response format is unavailable
- **WHEN** an adapter cannot apply a native structured-output format
- **THEN** existing prompt guidance, parsing, validation, and warnings SHALL remain available according to the structured-output contract
