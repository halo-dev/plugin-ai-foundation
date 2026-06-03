## ADDED Requirements

### Requirement: Lifecycle Callbacks Observe Tool Approval
The system SHALL expose approval-related tool states through provider-neutral lifecycle events.

#### Scenario: Tool approval requested
- **WHEN** the system determines that a valid tool call requires approval
- **THEN** lifecycle callbacks SHALL be able to observe the approval request with approval id, tool call id, tool name, parsed input, and step index
- **AND** the tool-call-start callback SHALL NOT be invoked for that pending tool during the same request

#### Scenario: Approved tool executes later
- **WHEN** a later request approves a pending tool call
- **THEN** tool lifecycle callbacks SHALL wrap the actual executor invocation in that later request
- **AND** generated finish events SHALL include the resulting tool result or tool error

#### Scenario: Denied tool does not execute
- **WHEN** a later request denies a pending tool call
- **THEN** the system SHALL NOT invoke tool-call-start or tool-call-finish callbacks for an executor invocation
- **AND** generated finish events SHALL include the denial as provider-neutral tool history

### Requirement: Approval Callback Failures Are Safe
Approval-related lifecycle callbacks SHALL NOT change whether a tool is approved, denied, or executed.

#### Scenario: Approval observer fails
- **WHEN** an approval-related lifecycle callback throws an exception
- **THEN** generation SHALL continue when possible
- **AND** the failure SHALL be surfaced as a provider-neutral warning with a safe message
