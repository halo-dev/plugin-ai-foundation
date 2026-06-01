## ADDED Requirements

### Requirement: Structured Output Ignores Extracted Reasoning
Structured output parsing and validation SHALL use answer text after reasoning extraction.

#### Scenario: Object output strips tagged reasoning before parsing
- **WHEN** a provider returns tagged reasoning followed by a JSON object for an object output request
- **THEN** the system SHALL extract the reasoning into typed reasoning fields
- **AND** it SHALL parse and validate the JSON object from the remaining answer text

#### Scenario: Array output strips tagged reasoning before parsing
- **WHEN** a provider returns tagged reasoning followed by a JSON array for an array output request
- **THEN** the system SHALL extract the reasoning into typed reasoning fields
- **AND** it SHALL parse and validate array elements from the remaining answer text

#### Scenario: Choice output strips tagged reasoning before validation
- **WHEN** a provider returns tagged reasoning followed by a choice value
- **THEN** the system SHALL extract the reasoning into typed reasoning fields
- **AND** it SHALL validate the remaining answer text against the allowed choices

#### Scenario: Structured validation error uses cleaned text context
- **WHEN** structured output validation fails after reasoning extraction
- **THEN** validation diagnostics SHALL describe the cleaned answer text used for parsing
- **AND** extracted reasoning SHALL remain available on the generation result when a result object is produced
