## ADDED Requirements

### Requirement: Approval Flows Return Persistable Message History
Tool approval flows SHALL expose response messages that let callers persist pending, approved, denied, and consumed approval state without replaying tools.

#### Scenario: Pending approval returns assistant message
- **WHEN** a generation produces a tool call that requires approval
- **THEN** response messages SHALL include an assistant message containing the tool-call part and the matching tool-approval-request part
- **AND** response messages SHALL NOT include a tool result or tool error for that pending call during the same request

#### Scenario: Approved approval returns consumed tool history
- **WHEN** a later request includes a matching approved tool-approval-response
- **AND** the system executes the approved tool
- **THEN** response messages SHALL include the generated tool result or tool error that marks the approval as consumed
- **AND** callers who persist those response messages SHALL NOT cause the same approval response to execute the tool again on a later request

#### Scenario: Denied approval returns consumed denial history
- **WHEN** a later request includes a matching denied tool-approval-response
- **THEN** response messages SHALL include the generated denial tool error or denial result that marks the approval as consumed
- **AND** callers who persist those response messages SHALL NOT cause the same denied approval to be resolved again on a later request

#### Scenario: Approval response remains caller-supplied history
- **WHEN** a caller resumes a pending approval by sending a tool-approval-response message
- **THEN** the response messages SHALL NOT duplicate that caller-supplied approval response
- **AND** documentation SHALL instruct callers to persist both their approval response input and the returned consumed result or error history
