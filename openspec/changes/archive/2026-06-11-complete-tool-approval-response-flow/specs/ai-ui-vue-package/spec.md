## MODIFIED Requirements

### Requirement: Tool continuation helpers
The package SHALL support frontend continuation of dynamic Halo tool parts.

#### Scenario: Add tool output
- **WHEN** a caller adds a tool output for a pending dynamic tool part
- **THEN** the package SHALL update the matching `tool-*` part to state `output-available`
- **AND** it SHALL resolve the tool name from existing assistant message parts when the caller only provides `toolCallId`

#### Scenario: Add tool error
- **WHEN** a caller adds a tool error for a pending dynamic tool part
- **THEN** the package SHALL update the matching `tool-*` part to state `output-error`
- **AND** `output-error` SHALL count as a completed tool lifecycle

#### Scenario: Add approved tool approval response
- **WHEN** a caller approves a pending server-side tool approval through `addToolApprovalResponse`
- **THEN** the package SHALL update the matching `tool-*` part to state `approval-responded`
- **AND** it SHALL preserve `approval.approved = true`
- **AND** it SHALL NOT create a tool output for the approved tool before the backend returns one

#### Scenario: Add denied tool approval response
- **WHEN** a caller denies a pending server-side tool approval through `addToolApprovalResponse`
- **THEN** the package SHALL update the matching `tool-*` part to state `approval-responded`
- **AND** it SHALL preserve `approval.approved = false`
- **AND** it SHALL preserve the optional denial reason on the approval response
- **AND** it SHALL NOT update the part to `output-error`

#### Scenario: Reject tool call delegates to approval response
- **WHEN** a caller rejects a pending tool approval through `rejectToolCall`
- **THEN** the package SHALL delegate to `addToolApprovalResponse` with `approved = false`
- **AND** it SHALL produce the same `approval-responded` message state

#### Scenario: Automatic continuation
- **WHEN** a tool helper changes messages and `sendAutomaticallyWhen` returns true
- **THEN** the chat SHALL submit the updated message history without requiring the caller to invoke `sendMessage`

#### Scenario: Built-in tool completion predicate
- **WHEN** the last assistant message has only completed dynamic tool parts
- **THEN** `isLastAssistantMessageToolComplete` SHALL return true
- **AND** pending `input-streaming`, `input-available`, `approval-requested`, or `approval-responded` states SHALL make it return false

## ADDED Requirements

### Requirement: Approval response continuation predicate
The package SHALL expose a helper that decides when the last assistant message is ready to continue after server-side tool approval responses.

#### Scenario: Approval responses complete the approval step
- **WHEN** the last assistant message contains at least one `tool-*` part in `approval-responded`
- **AND** it contains no `tool-*` part in `approval-requested`
- **THEN** `lastAssistantMessageIsCompleteWithApprovalResponses` SHALL return true

#### Scenario: Pending approval blocks continuation
- **WHEN** the last assistant message contains any `tool-*` part in `approval-requested`
- **THEN** `lastAssistantMessageIsCompleteWithApprovalResponses` SHALL return false

#### Scenario: No approval step does not trigger continuation
- **WHEN** the last assistant message contains no `tool-*` part in `approval-requested` or `approval-responded`
- **THEN** `lastAssistantMessageIsCompleteWithApprovalResponses` SHALL return false
