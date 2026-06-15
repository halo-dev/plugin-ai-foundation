## MODIFIED Requirements

### Requirement: UI Message Tool Approval Response Part
The SDK SHALL provide persisted UI Message tool lifecycle state for caller approval or denial of a pending tool approval request.

#### Scenario: Approval response is persisted on the tool part
- **WHEN** a caller stores a UI message after approving or denying a tool approval request
- **THEN** the matching dynamic `tool-*` part SHALL have state `approval-responded`
- **AND** the UI message role remains `ASSISTANT`

#### Scenario: Approval response required fields
- **WHEN** an approval response is validated
- **THEN** it MUST include an approval id
- **AND** it MUST include an approved decision
- **AND** tool call id, tool name, reason, and provider metadata SHALL be preserved when present

#### Scenario: Duplicate approval response is invalid
- **WHEN** a UI message history contains more than one approval response for the same approval id
- **THEN** validation fails with a duplicate approval response issue

#### Scenario: Denied approval does not synthesize tool error
- **WHEN** a caller denies a tool approval request
- **THEN** the SDK stores `approval-responded` with `approved = false`
- **AND** it SHALL NOT synthesize `output-error` or any tool execution error for the denial

### Requirement: UI Message Tool Continuation Validation
The SDK SHALL validate persisted dynamic tool lifecycle state before converting UI messages to model messages.

#### Scenario: Tool output references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-available`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Tool error references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-error`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Denied output references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-denied`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Terminal tool output is unique
- **WHEN** a UI message history contains multiple conflicting terminal states for the same `toolCallId`
- **THEN** validation fails

#### Scenario: Approval response is a continuation boundary
- **WHEN** a user approves or denies a pending tool approval
- **THEN** the persisted tool part state SHALL be `approval-responded`
- **AND** validation SHALL allow the caller to continue generation from that state

#### Scenario: Pending tool state is allowed
- **WHEN** a UI message history contains a pending dynamic tool part without output
- **THEN** validation allows the history
- **AND** the caller decides when to continue generation

### Requirement: UI Message Tool Boundary Conversion
The SDK SHALL convert persisted assistant UI messages to provider-neutral model messages while preserving dynamic tool boundaries.

#### Scenario: Tool output splits assistant segments
- **WHEN** an assistant UI message contains assistant parts, dynamic tool output parts, and later assistant parts
- **THEN** conversion emits assistant model content before the tool output
- **AND** emits tool model content for the dynamic tool output
- **AND** emits later assistant model content after the tool output

#### Scenario: Consecutive tool outputs share tool message
- **WHEN** multiple consecutive dynamic tool parts are in `output-available`, `output-error`, or `output-denied` state
- **THEN** conversion SHALL preserve them as consecutive tool model content

#### Scenario: Approved approval response converts to approval response history
- **WHEN** an assistant UI message contains a dynamic tool part in `approval-responded` state with `approved = true`
- **THEN** conversion SHALL emit the original assistant tool call and approval request
- **AND** conversion SHALL emit a matching tool approval response with `approved = true`
- **AND** conversion SHALL NOT emit a tool result before the backend executes the approved tool

#### Scenario: Denied approval response converts to approval response history
- **WHEN** an assistant UI message contains a dynamic tool part in `approval-responded` state with `approved = false`
- **THEN** conversion SHALL emit the original assistant tool call and approval request
- **AND** conversion SHALL emit a matching tool approval response with `approved = false`
- **AND** conversion SHALL NOT emit a tool execution error for the denial

### Requirement: Dynamic tool part lifecycle
The SDK SHALL model each tool call as one dynamic `tool-*` part whose state represents the current lifecycle.

#### Scenario: Tool input streams into one part
- **WHEN** the stream emits tool input chunks for a tool call id
- **THEN** the reader SHALL create or update one matching `tool-*` part with state `input-streaming`

#### Scenario: Tool input availability is persisted
- **WHEN** the stream emits a complete tool input for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `input-available`
- **AND** the part SHALL expose the parsed input

#### Scenario: Tool approval waits on the same part
- **WHEN** the stream emits a tool approval request for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `approval-requested`
- **AND** the part SHALL expose approval id and input needed by the UI

#### Scenario: Tool approval response updates the same part
- **WHEN** a caller supplies an approval response for a tool approval id
- **THEN** the matching `tool-*` part SHALL have state `approval-responded`
- **AND** the part SHALL expose the approved decision and optional reason

#### Scenario: Tool output completes the same part
- **WHEN** a caller supplies a tool output for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `output-available`
- **AND** the part SHALL expose the output

#### Scenario: Tool error completes the same part
- **WHEN** a caller supplies a tool error for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `output-error`
- **AND** the part SHALL expose safe error text

#### Scenario: Tool denial completes the same part
- **WHEN** the backend reports that a tool was not executed because approval was denied
- **THEN** the matching `tool-*` part SHALL have state `output-denied`
- **AND** the part SHALL expose the denial reason when available

## ADDED Requirements

### Requirement: UI Message Tool Approval Documentation
The SDK documentation SHALL describe tool approval continuation using the same lifecycle states as the runtime.

#### Scenario: UI Message guide documents approval response state
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain `approval-requested`, `approval-responded`, and `output-denied`
- **AND** the guide SHALL state that denied approvals are not execution errors

#### Scenario: UI Message guide documents automatic continuation boundary
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain that approval APIs record decisions
- **AND** automatic continuation remains controlled by the chat continuation predicate
