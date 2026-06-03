## ADDED Requirements

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
- **AND** the system SHALL NOT invoke that tool executor during the same call

#### Scenario: Streaming call needs approval
- **WHEN** `LanguageModel.streamText(request)` receives a valid streamed tool call that requires approval
- **THEN** `fullStream()` SHALL emit a `tool-approval-request` part after the corresponding completed `tool-call` part
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
