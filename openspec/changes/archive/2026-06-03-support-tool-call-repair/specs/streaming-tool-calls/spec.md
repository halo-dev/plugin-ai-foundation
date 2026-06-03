## ADDED Requirements

### Requirement: Streaming Tool Call Repair
Streaming tool execution SHALL apply the same invalid-input repair semantics as non-streaming generation.

#### Scenario: Stream repairs invalid input before emitting result
- **WHEN** a streamed provider step finishes with a known executable tool call whose input fails validation
- **AND** the request includes a tool-call repair callback
- **AND** the callback returns valid repaired input
- **THEN** `fullStream()` SHALL emit one `tool-call` part for the repaired tool call
- **AND** it SHALL emit the corresponding `tool-result` after server-side execution
- **AND** it SHALL emit a `finish-step` warning indicating that repair occurred

#### Scenario: Stream repair failure emits tool error
- **WHEN** a streamed provider step finishes with invalid tool-call input
- **AND** repair is unavailable or unsuccessful
- **THEN** `fullStream()` SHALL emit a `tool-call` part for the original model-produced call
- **AND** it SHALL emit a `tool-error` part with the safe validation error
- **AND** it SHALL finish the current step without starting a continuation step from that failed tool call

#### Scenario: Stream continuation uses repaired history
- **WHEN** a streamed repaired tool call succeeds
- **AND** `stopWhen` allows another provider step
- **THEN** the next provider stream SHALL receive assistant tool-call history containing the repaired input
- **AND** it SHALL receive the matching tool result history

#### Scenario: Text stream excludes repair diagnostics
- **WHEN** `StreamTextResult.textStream()` is consumed for a generation that repairs a tool call
- **THEN** it SHALL emit only answer text deltas
- **AND** it SHALL NOT emit serialized repaired tool calls, repair warnings, tool results, or response messages as answer text

#### Scenario: Multiple projections do not duplicate repair
- **WHEN** multiple projections are consumed from one `StreamTextResult` whose tool call is repaired
- **THEN** the repair callback SHALL be invoked at most once for that tool call
- **AND** the server-side executor SHALL be invoked at most once for the repaired call
