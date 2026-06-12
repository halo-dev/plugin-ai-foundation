## ADDED Requirements

### Requirement: Canonical tool stream chunks
The SDK SHALL expose canonical UI message stream chunks for tool lifecycle events instead of using dynamic `tool-<name>` chunk types as the external wire protocol.

#### Scenario: Tool input start maps to canonical chunk
- **WHEN** a `StreamTextResult` emits a `tool-input-start` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-start`
- **AND** the chunk SHALL carry `toolCallId` and `toolName`

#### Scenario: Tool input delta maps to canonical chunk
- **WHEN** a `StreamTextResult` emits a `tool-input-delta` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-delta`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, and `inputTextDelta`

#### Scenario: Tool call maps to input available
- **WHEN** a `StreamTextResult` emits a completed `tool-call` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-available`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, parsed `input`, and provider metadata when present

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

### Requirement: Step start UI lifecycle chunks
The SDK SHALL preserve `start-step` as a UI message stream lifecycle chunk without adding it to accumulated UI message parts.

#### Scenario: Start step is emitted
- **WHEN** a `StreamTextResult` emits a `start-step` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `start-step`
- **AND** the chunk SHALL carry the step index when present

#### Scenario: Reader does not persist start step
- **WHEN** a UI message stream reader receives a `start-step` chunk
- **THEN** the response message parts SHALL remain unchanged by that chunk
- **AND** terminal summary SHALL remain unchanged until a terminal or finish-step chunk is received

### Requirement: Source and file scope remains real
The SDK SHALL keep existing `source-url` and `file` UI message stream behavior without adding document-source placeholders.

#### Scenario: Source remains URL based
- **WHEN** a `StreamTextResult` emits the currently supported source part
- **THEN** `toUIMessageStream()` SHALL continue emitting `source-url`
- **AND** it SHALL NOT emit `source-document` without a backend document-source part

#### Scenario: File behavior is preserved
- **WHEN** a `StreamTextResult` emits a file part
- **THEN** `toUIMessageStream()` SHALL continue emitting a `file` chunk with the existing fields
- **AND** this change SHALL NOT introduce upload management or new file source semantics
