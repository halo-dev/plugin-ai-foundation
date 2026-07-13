## MODIFIED Requirements

### Requirement: Workbench supports structured output testing
The Console model test workbench SHALL exercise structured output requests using typed settings only.

#### Scenario: Structured output request from workbench
- **WHEN** a super administrator selects structured output and supplies schema or choices
- **THEN** the next test request SHALL include the corresponding `GenerateTextRequest.output`
- **AND** it SHALL preserve system, history, typed parameters, and tools without provider-native option maps

#### Scenario: Structured output stream display
- **WHEN** a stream returns structured JSON text
- **THEN** the workbench SHALL display it as assistant answer text
- **AND** reasoning and tool activity rendering SHALL remain unchanged

#### Scenario: Structured output validation error display
- **WHEN** a structured stream emits a validation error
- **THEN** the workbench SHALL display the safe error on the active assistant message
- **AND** the request SHALL no longer be marked loading

#### Scenario: Stop condition reached before structured output
- **WHEN** tool calling reaches the step limit before a final structured answer
- **THEN** the result or stream SHALL include the existing stop-condition warning
- **AND** structured output validation SHALL fail if no valid output exists

