## ADDED Requirements

### Requirement: Workbench Supports External Tool Execution Testing
The model test workbench SHALL provide a real end-to-end flow for testing no-executor external tools.

#### Scenario: External test tool returns pending call
- **WHEN** the administrator enables the external tool test option and sends a chat message
- **THEN** the backend test endpoint SHALL expose a request-scoped tool without a server-side executor
- **AND** the workbench SHALL display the returned `tool-call` event without appending it to assistant answer text
- **AND** the assistant message SHALL persist returned response messages for later continuation

#### Scenario: Administrator supplies external result
- **WHEN** an assistant message contains a pending external tool call
- **AND** the administrator submits a JSON result for that tool call
- **THEN** the workbench SHALL append a tool message containing a matching `tool-result`
- **AND** it SHALL send the updated conversation to the streaming test endpoint
- **AND** the next assistant response SHALL be generated from the externally supplied result

#### Scenario: Administrator supplies external error
- **WHEN** an assistant message contains a pending external tool call
- **AND** the administrator submits an error message for that tool call
- **THEN** the workbench SHALL append a tool message containing a matching `tool-error`
- **AND** it SHALL send the updated conversation to the streaming test endpoint
- **AND** the next assistant response SHALL be generated from the externally supplied error

#### Scenario: External result is not replayed
- **WHEN** the administrator regenerates or continues after an external tool result has already been appended
- **THEN** the workbench SHALL include the persisted assistant tool-call message and the caller-supplied tool result once
- **AND** it SHALL NOT synthesize an additional result from display-only tool events

#### Scenario: Pending external call remains visible
- **WHEN** a generation finishes with a pending external tool call
- **THEN** the workbench SHALL keep the tool call visible with a pending external status
- **AND** it SHALL provide result and error submission controls associated with that exact tool call
