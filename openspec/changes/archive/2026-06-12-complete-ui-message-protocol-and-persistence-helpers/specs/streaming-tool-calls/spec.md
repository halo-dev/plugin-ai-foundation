## ADDED Requirements

### Requirement: Tool stream events remain executable after UI projection
Tool calls exposed through UI message streams SHALL preserve enough information for client-side or approval-based continuation.

#### Scenario: Client tool callback receives canonical input availability
- **WHEN** the UI message reducer accepts a `tool-input-available` chunk for a client-side tool
- **THEN** `onToolCall` SHALL receive the final dynamic `tool-<name>` part with the parsed input
- **AND** the callback SHALL NOT receive partial `tool-input-delta` events as executable calls

#### Scenario: Approval request remains pending work
- **WHEN** the UI message reducer accepts a tool approval request chunk
- **THEN** the final dynamic tool part SHALL be in `approval-requested` state
- **AND** automatic continuation predicates SHALL treat the message as pending until an approval response is added

#### Scenario: Approval denial is not runtime failure
- **WHEN** the UI message reducer accepts or creates a denied approval response
- **THEN** the final dynamic tool part SHALL be in `approval-responded` state with `approval.approved = false`
- **AND** the chat runtime SHALL NOT expose the denial as a stream or schema error

#### Scenario: Server result closes approved tool
- **WHEN** the UI message reducer later accepts a `tool-output-available` chunk for an approved tool
- **THEN** the final dynamic tool part SHALL move to `output-available`
- **AND** it SHALL preserve the prior approval response details when present
