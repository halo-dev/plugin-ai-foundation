## ADDED Requirements

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
