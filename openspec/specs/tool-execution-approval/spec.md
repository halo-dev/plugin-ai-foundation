# tool-execution-approval Specification

## Purpose
TBD - created by archiving change tool-execution-approval. Update Purpose after archive.
## Requirements
### Requirement: Tools Declare Approval Policy
The system SHALL allow request-scoped tool definitions to declare whether server-side execution requires approval.

#### Scenario: Tool never requires approval
- **WHEN** a tool definition does not configure approval
- **THEN** the tool SHALL keep the existing behavior and execute after schema validation when step control allows execution

#### Scenario: Tool always requires approval
- **WHEN** a tool definition declares that approval is always required
- **AND** the model emits a valid call for that tool
- **THEN** the system SHALL create a tool approval request instead of invoking the tool executor

#### Scenario: Tool dynamically requires approval
- **WHEN** a tool definition declares a dynamic approval predicate
- **AND** the model emits a valid call for that tool
- **THEN** the system SHALL evaluate the predicate with provider-neutral tool execution context before invoking the executor
- **AND** the executor SHALL be invoked only when the predicate returns that approval is not required or a matching approval response has approved the call

### Requirement: Approval Requests Are Returned Instead Of Blocking
The system SHALL complete generation calls with approval request data instead of suspending while waiting for an external decision.

#### Scenario: Non-streaming call needs approval
- **WHEN** `LanguageModel.generateText(request)` receives a valid tool call that requires approval
- **THEN** the returned result SHALL include a `tool-approval-request` content part with approval id, tool call id, tool name, parsed input, step index, and provider metadata when available
- **AND** the response messages SHALL include the same step index in the persistable approval request message part
- **AND** the system SHALL NOT invoke that tool executor during the same call

#### Scenario: Streaming call needs approval
- **WHEN** `LanguageModel.streamText(request)` receives a valid streamed tool call that requires approval
- **THEN** `fullStream()` SHALL emit a `tool-approval-request` part after the corresponding completed `tool-call` part
- **AND** the stream part SHALL include the approval request step index
- **AND** the final stream result response messages SHALL include the same step index in the persistable approval request message part
- **AND** the system SHALL NOT emit a `tool-result` or `tool-error` part for that pending call during the same request

#### Scenario: Pending approval stops the current loop
- **WHEN** a generation step produces any tool call that requires approval and has no matching approval response
- **THEN** the system SHALL finish the current generation call without executing any tool executors from that step
- **AND** it SHALL NOT start a follow-up provider step for that step's tool calls

### Requirement: Approval Responses Resume Tool Execution
The system SHALL resume pending approved or denied tool calls from normal `ModelMessage` history.

#### Scenario: Approved response executes pending tool
- **WHEN** a later request includes an assistant `tool-approval-request` part and a matching tool `tool-approval-response` part with `approved = true`
- **AND** no matching tool result or tool error already exists later in the supplied history
- **THEN** the system SHALL execute the original tool call before invoking the provider
- **AND** the tool execution context SHALL use the original approval request step index when it is present
- **AND** it SHALL append the resulting tool history so the provider can continue from the executed result

#### Scenario: Denied response informs model
- **WHEN** a later request includes an assistant `tool-approval-request` part and a matching tool `tool-approval-response` part with `approved = false`
- **AND** no matching tool result or tool error already exists later in the supplied history
- **THEN** the system SHALL NOT invoke the tool executor
- **AND** it SHALL append a safe denial tool error or denial result that the provider can use to continue the conversation

#### Scenario: Response references unknown approval
- **WHEN** a request includes a `tool-approval-response` that does not match a prior approval request in the supplied messages
- **THEN** the system SHALL fail request validation before invoking the provider

#### Scenario: Approval response has already been consumed
- **WHEN** a request includes an approval response that is followed by a matching tool result or tool error in the supplied messages
- **THEN** the system SHALL treat the approval as already resolved
- **AND** it SHALL NOT execute or deny that tool call again

### Requirement: Approval Identity Is Provider-Neutral
The system SHALL correlate approval requests and responses without exposing provider-native objects.

#### Scenario: Approval id is generated
- **WHEN** the system creates a tool approval request
- **THEN** it SHALL include a non-blank approval id
- **AND** it SHALL include the original tool call id when available

#### Scenario: Approval response matches request
- **WHEN** the system resolves a tool approval response
- **THEN** it SHALL match by approval id
- **AND** it SHALL verify that the tool call id and tool name are consistent with the approval request when those fields are supplied

### Requirement: Approval Flows Return Persistable Message History
Tool approval flows SHALL expose response messages that let callers persist pending, approved, denied, and consumed approval state without replaying tools.

#### Scenario: Pending approval returns assistant message
- **WHEN** a generation produces a tool call that requires approval
- **THEN** response messages SHALL include an assistant message containing the tool-call part and the matching tool-approval-request part
- **AND** the tool-approval-request part SHALL preserve the approval request step index
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

### Requirement: Mixed Approval Steps Must Not Leave Unresolved Executable Calls
The system SHALL avoid recording executable tool calls as unresolved provider history when any tool call in the same step requires approval.

#### Scenario: Non-streaming mixed executable and approval-required tool calls pause safely
- **WHEN** `LanguageModel.generateText(request)` receives a provider step with one executable server-side tool call and one approval-required tool call
- **THEN** the generation SHALL return the approval request and finish the current call without starting a follow-up provider step
- **AND** response messages SHALL NOT contain an unrelated executable tool call unless that call also has a matching tool result or tool error in the same response history
- **AND** the approval-required tool call SHALL remain available for approval response resumption

#### Scenario: Streaming mixed executable and approval-required tool calls pause safely
- **WHEN** `LanguageModel.streamText(request)` receives a streamed provider step with one executable server-side tool call and one approval-required tool call
- **THEN** `fullStream()` SHALL emit the approval request and finish the current stream without starting a follow-up provider step
- **AND** `StreamTextResult.result()` SHALL NOT expose unresolved unrelated executable tool calls in response messages
- **AND** no server-side executor SHALL run for the approval-required tool during the same request

#### Scenario: Approved resumption uses complete consumed history
- **WHEN** a later request includes a matching approved tool-approval-response for a pending approval request
- **THEN** the system SHALL execute or resolve only the pending approved tool call represented by that approval request
- **AND** it SHALL append a matching tool result or tool error before invoking the provider continuation
- **AND** it SHALL NOT rely on unresolved executable tool calls from the earlier paused step
