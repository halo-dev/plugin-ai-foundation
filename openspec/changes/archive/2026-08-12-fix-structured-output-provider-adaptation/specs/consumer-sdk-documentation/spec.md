## ADDED Requirements

### Requirement: Structured output strictness and fallback are documented
Consumer documentation SHALL explain portable strict schemas, non-strict schemas, provider-native
formats, downgrade warnings, and local validation without requiring provider-specific API types.

#### Scenario: Strict schema requirements are documented
- **WHEN** a plugin author reads the structured-output guide
- **THEN** the guide SHALL explain closed objects, required properties, nested object rules, and
  nullable values
- **AND** it SHALL show how to opt into strict output with `OutputSpec.strict`

#### Scenario: Non-strict semantics are documented
- **WHEN** a plugin author omits strict or sets it to false
- **THEN** the guide SHALL state that the schema is not rewritten or upgraded to native strict mode
- **AND** it SHALL explain that final local validation still applies

#### Scenario: Provider fallback is documented
- **WHEN** the selected provider offers JSON Object or prompt-only behavior instead of native JSON
  Schema enforcement
- **THEN** the guide SHALL explain the stable downgrade and strict-not-guaranteed warnings
- **AND** it SHALL explain that provider acceptance does not replace final local validation

#### Scenario: Invalid output diagnostics are documented
- **WHEN** provider output cannot be parsed or validated
- **THEN** the guide SHALL explain how to inspect the typed validation error and safe diagnostic
  fields
- **AND** it SHALL caution against exposing raw model output to untrusted users

#### Scenario: Finish-reason termination is documented
- **WHEN** structured output is incomplete because of an explicit provider finish reason
- **THEN** the guide SHALL distinguish typed termination from ordinary JSON/schema validation
- **AND** it SHALL show how to inspect normalized reason, raw reason, usage, and response metadata
- **AND** it SHALL state that plain-text `LENGTH` responses remain successful partial results
