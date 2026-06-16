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

#### Scenario: Automatic continuation remains opt-in
- **WHEN** a tool helper changes messages and no `sendAutomaticallyWhen` predicate is configured
- **THEN** the chat SHALL NOT automatically submit the updated message history

#### Scenario: Automatic continuation can run multiple bounded steps
- **WHEN** a tool helper changes messages, `sendAutomaticallyWhen` returns true, and the automatic step limit has not been reached
- **THEN** the chat SHALL submit the updated message history without requiring the caller to invoke `sendMessage`
- **AND** a later distinct completed tool result state SHALL be eligible for another automatic continuation in the same chain

#### Scenario: Automatic continuation consumes completed tool continuations once
- **WHEN** a completed tool continuation has already triggered an automatic request
- **THEN** the same completed tool continuation SHALL NOT trigger another automatic request
- **AND** this SHALL remain true if the continuation response appends text, receives finish chunks, or uses a different assistant message id

#### Scenario: Automatic continuation stops at limit
- **WHEN** automatic continuation would exceed `maxAutomaticSteps`
- **THEN** the chat status SHALL remain `ready`
- **AND** the chat SHALL NOT submit another request
- **AND** the chat SHALL invoke `onAutomaticStepLimitExceeded` when provided

#### Scenario: Automatic continuation errors surface
- **WHEN** `sendAutomaticallyWhen` throws or rejects
- **THEN** the chat status SHALL become `error`
- **AND** the chat SHALL expose the thrown error through `error`
- **AND** the chat SHALL invoke `onError`

#### Scenario: Built-in tool completion predicate
- **WHEN** the last assistant message has at least one dynamic tool part and every dynamic tool part has a completed tool result state
- **THEN** `lastAssistantMessageIsCompleteWithToolCalls` SHALL return true
- **AND** `output-available`, `output-error`, and `output-denied` SHALL count as completed tool result states
- **AND** pending `input-streaming`, `input-available`, `approval-requested`, or `approval-responded` states SHALL make it return false

#### Scenario: Built-in approval response predicate
- **WHEN** the last assistant message has at least one dynamic tool approval part and every approval part has state `approval-responded`
- **THEN** `lastAssistantMessageHasRespondedToToolApprovals` SHALL return true
- **AND** pending `approval-requested` states SHALL make it return false

#### Scenario: Built-in completed continuation predicate
- **WHEN** the last assistant message has at least one dynamic tool part and every dynamic tool part has a continuation-ready state
- **THEN** `lastAssistantMessageHasCompletedToolContinuations` SHALL return true
- **AND** `output-available`, `output-error`, `output-denied`, and `approval-responded` SHALL count as continuation-ready states
- **AND** pending `input-streaming`, `input-available`, or `approval-requested` states SHALL make it return false

### Requirement: Chat tool callbacks
The `Chat` core SHALL expose client-side tool execution through `onToolCall`.

#### Scenario: onToolCall fires on input availability
- **WHEN** a dynamic tool part first reaches state `input-available`
- **THEN** `onToolCall` SHALL be called once for that tool call id

#### Scenario: onToolCall observes committed state
- **WHEN** `onToolCall` is called for an `input-available` dynamic tool part
- **THEN** the matching assistant message and tool part SHALL already be available through the chat messages state

#### Scenario: onToolCall does not auto-write output
- **WHEN** `onToolCall` returns a value or promise
- **THEN** the runtime SHALL NOT automatically convert that return value into tool output
- **AND** the caller SHALL still use `addToolOutput` or `addToolApprovalResponse` to update tool state

#### Scenario: onToolCall callback failure surfaces
- **WHEN** `onToolCall` throws synchronously or returns a rejected promise
- **THEN** the chat status SHALL become `error`
- **AND** the chat SHALL expose the callback failure through `error`
- **AND** the chat SHALL invoke `onError`
