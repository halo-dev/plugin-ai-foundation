## MODIFIED Requirements

### Requirement: Dynamic tool part lifecycle
The SDK SHALL model each tool call as one dynamic `tool-*` part whose state represents the current lifecycle and whose streaming input is best-effort parsed from private accumulated text.

#### Scenario: Tool input starts one part
- **WHEN** the reader receives `tool-input-start`
- **THEN** it SHALL create or reset one matching `tool-*` part with state `input-streaming`
- **AND** duplicate start for the same tool call id SHALL reset private accumulated input

#### Scenario: Tool input delta updates partial input
- **WHEN** the reader receives `tool-input-delta` after start
- **THEN** it SHALL append the text to private reducer state and attempt partial JSON repair and parsing
- **AND** the public part SHALL expose only the best parsed `input` or `undefined`, not accumulated raw input text
- **AND** partial parsing failure SHALL NOT terminate the stream

#### Scenario: Delta without start is invalid
- **WHEN** the reader receives `tool-input-delta` without prior start state for that tool call id
- **THEN** it SHALL report a protocol error

#### Scenario: Tool input availability is persisted
- **WHEN** the stream emits complete tool input for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `input-available`
- **AND** authoritative final input SHALL replace any partially parsed input
- **AND** availability without a prior start SHALL be accepted

#### Scenario: Interrupted partial input remains visible
- **WHEN** the outer stream errors or is cancelled after partial tool input
- **THEN** the matching part SHALL remain in `input-streaming` with its last parsed input
- **AND** the reader SHALL NOT fabricate an input-error transition

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

### Requirement: Canonical tool stream chunks
The SDK SHALL expose canonical UI message stream chunks for tool lifecycle events instead of using dynamic `tool-<name>` chunk types as the external wire protocol.

#### Scenario: Tool input start maps to canonical chunk
- **WHEN** a `StreamTextResult` emits a `tool-input-start` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-start`
- **AND** the chunk SHALL carry `toolCallId` and `toolName`

#### Scenario: Tool input delta maps to canonical chunk
- **WHEN** a `StreamTextResult` emits a `tool-input-delta` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-delta`
- **AND** the chunk SHALL carry only `toolCallId` and appendable `inputTextDelta`
- **AND** the tool name SHALL be resolved from the matching input-start state

#### Scenario: Tool input end is backend-only
- **WHEN** a `StreamTextResult` emits a `tool-input-end` part
- **THEN** `toUIMessageStream()` SHALL NOT emit a corresponding UI chunk

#### Scenario: Tool call maps to input available
- **WHEN** a `StreamTextResult` emits a completed `tool-call` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-available`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, authoritative parsed `input`, and provider metadata when present

#### Scenario: Tool input error maps to canonical error
- **WHEN** a `StreamTextResult` emits a `tool-input-error` part
- **THEN** `toUIMessageStream()` SHALL emit a canonical input-error chunk with stable identity and safe error text
- **AND** it SHALL NOT invoke `onToolCall` for that invalid input

#### Scenario: Tool result maps to output available
- **WHEN** a `StreamTextResult` emits a `tool-result` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-output-available`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, output payload, and provider metadata when present

#### Scenario: Tool error maps to output error
- **WHEN** a `StreamTextResult` emits a `tool-error` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-output-error`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, safe `errorText`, and provider metadata when present

#### Scenario: Tool approval maps to approval chunks
- **WHEN** a `StreamTextResult` emits tool approval request or response parts
- **THEN** `toUIMessageStream()` SHALL emit canonical approval chunks
- **AND** the chunks SHALL preserve approval id, approval decision, reason, input, and provider metadata when present

## ADDED Requirements

### Requirement: Partial tool JSON parsing is self-contained
The npm SDK SHALL parse incomplete streamed tool JSON without adding a runtime dependency or exposing repair internals.

#### Scenario: Repairable incomplete JSON
- **WHEN** accumulated argument text is incomplete but can be repaired into parseable JSON
- **THEN** the reducer SHALL expose the parsed partial value as tool input

#### Scenario: Unrepairable incomplete JSON
- **WHEN** accumulated argument text cannot yet be repaired and parsed
- **THEN** the reducer SHALL expose `undefined` for partial input
- **AND** it SHALL continue accepting later deltas

#### Scenario: Final input wins
- **WHEN** authoritative input availability follows any partial parsing outcome
- **THEN** the reducer SHALL discard private accumulated text for that lifecycle
- **AND** the public tool part SHALL expose exactly the authoritative final input

### Requirement: Console workbench exposes tool input stream diagnostics
The console model test workbench SHALL provide an opt-in test unit for observing the end-to-end tool input lifecycle without fabricating provider deltas.

#### Scenario: Dedicated test tool exercises the complete path
- **WHEN** an administrator selects the tool input stream example and sends it with a language model
- **THEN** the workbench SHALL enable a dedicated schema-constrained test tool
- **AND** the tool SHALL record backend input-start, input-delta, and input-available callbacks
- **AND** its normal tool result SHALL include the backend callback order and delta count

#### Scenario: Browser diagnostics classify real deltas
- **WHEN** the workbench receives canonical tool input chunks
- **THEN** it SHALL record the raw per-call event order before UI message reduction
- **AND** it SHALL display start, delta count, availability or error, accumulated delta text, and authoritative input
- **AND** it SHALL label calls with at least one delta as real incremental streaming

#### Scenario: Final-only providers remain visible
- **WHEN** a provider emits authoritative tool input without provider-native deltas
- **THEN** the workbench SHALL label the call as final-only
- **AND** it SHALL NOT fabricate start or delta events
