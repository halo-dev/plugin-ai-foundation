## ADDED Requirements

### Requirement: Mixed Tool Calls Must Not Continue With Pending External Work
The system SHALL stop the current tool loop when a provider step contains any pending external tool call that has no server-side executor.

#### Scenario: Non-streaming mixed executable and external tool calls stop
- **WHEN** `LanguageModel.generateText(request)` receives a provider step with one executable server-side tool call and one no-executor external tool call
- **THEN** the generation SHALL finish the current call without starting a follow-up provider step
- **AND** response messages SHALL NOT contain an assistant tool call that is forwarded to a follow-up provider request without a matching tool result or tool error
- **AND** the pending external tool call SHALL remain available for caller-side execution

#### Scenario: Streaming mixed executable and external tool calls stop
- **WHEN** `LanguageModel.streamText(request)` receives a streamed provider step with one executable server-side tool call and one no-executor external tool call
- **THEN** `fullStream()` SHALL finish the current stream without starting a follow-up provider step
- **AND** `StreamTextResult.result()` SHALL expose response messages that callers can append after supplying the missing external tool result or error
- **AND** the stream SHALL NOT emit later answer text from a provider continuation that did not receive complete tool history

#### Scenario: Fully resolved executable tool calls can continue
- **WHEN** a provider step contains only executable server-side tool calls
- **AND** each executable call produces a tool result or tool error according to existing failure semantics
- **AND** `stopWhen` allows another step
- **THEN** the system SHALL append complete assistant tool-call history plus matching tool result history before starting the next provider step
