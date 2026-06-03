## MODIFIED Requirements

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
