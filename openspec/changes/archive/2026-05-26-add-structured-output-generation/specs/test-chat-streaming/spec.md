## ADDED Requirements

### Requirement: Workbench supports structured output testing
The Console model test workbench SHALL provide a minimal way to exercise structured output requests.

#### Scenario: Structured output request from workbench
- **WHEN** a super administrator selects structured output mode and enters a JSON Schema or choice options
- **THEN** the next test request SHALL include the corresponding `GenerateTextRequest.output`
- **AND** the request SHALL still include existing system prompt, message history, parameters, tools, and provider options

#### Scenario: Structured output stream display
- **WHEN** a test-chat stream returns structured JSON text
- **THEN** the workbench SHALL display that JSON as assistant answer text
- **AND** reasoning and tool activity rendering SHALL remain unchanged

#### Scenario: Structured output validation error display
- **WHEN** a structured stream emits an `error` part for validation failure
- **THEN** the workbench SHALL display the safe error message on the active assistant message
- **AND** the request SHALL no longer be marked as loading
