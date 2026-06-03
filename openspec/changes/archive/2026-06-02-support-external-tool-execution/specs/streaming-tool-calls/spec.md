## MODIFIED Requirements

### Requirement: Progressive streaming tool execution
The system SHALL keep language model streaming progressive when request-scoped server-side or external tools are present.

#### Scenario: Tool-enabled stream emits provider deltas before completion
- **WHEN** a caller invokes `LanguageModel.streamText(request)` with tools
- **AND** the provider emits text or reasoning deltas before the step finishes
- **THEN** the returned Flux SHALL emit corresponding stream parts before tool execution and before final completion
- **AND** the implementation SHALL NOT wait for all generation steps to finish before emitting the first model delta

#### Scenario: Completed tool call is emitted before execution result
- **WHEN** a streamed provider step finishes with a tool call
- **THEN** the stream SHALL emit a `tool-call` part containing the tool call id, tool name, and parsed input
- **AND** the `tool-call` part SHALL be emitted before the corresponding `tool-result` or `tool-error`

#### Scenario: Server-side tool result continues generation
- **WHEN** a streamed step returns an executable tool call
- **AND** `stopWhen` allows another provider step
- **THEN** the system SHALL execute the tool server-side
- **AND** emit a `tool-result` part after the executor completes
- **AND** append assistant tool-call history plus tool result history before starting the next streamed provider step

#### Scenario: No-executor tool call finishes as pending external work
- **WHEN** a streamed step returns a tool call whose request tool has no executor
- **THEN** `fullStream()` SHALL emit the completed `tool-call` part
- **AND** the current stream SHALL finish without emitting a `tool-result` or `tool-error` for that call
- **AND** `StreamTextResult.result()` SHALL expose response messages containing the assistant tool-call history needed for later external execution

## ADDED Requirements

### Requirement: Streaming External Tool Results Resume Generation
Streaming generation SHALL continue from caller-supplied external tool results and errors in message history.

#### Scenario: Stream resumes from external result
- **WHEN** `LanguageModel.streamText(request)` receives messages containing an assistant tool-call part and a matching external tool-result message
- **THEN** the provider stream SHALL be invoked with that history
- **AND** `fullStream()` SHALL emit later answer text from the provider continuation
- **AND** no server-side tool executor SHALL be required for the completed external tool

#### Scenario: Stream resumes from external error
- **WHEN** `LanguageModel.streamText(request)` receives messages containing an assistant tool-call part and a matching external tool-error message
- **THEN** the provider stream SHALL be invoked with that history
- **AND** `fullStream()` SHALL emit later answer text from the provider continuation
- **AND** no server-side tool executor SHALL run for that external error

#### Scenario: Text stream excludes pending external tool state
- **WHEN** `StreamTextResult.textStream()` is consumed for a generation that returns a pending external tool call
- **THEN** it SHALL emit only answer text deltas
- **AND** it SHALL NOT emit serialized tool call, external tool state, or response messages as answer text

#### Scenario: Multiple projections do not duplicate pending external calls
- **WHEN** multiple projections are consumed from one stream result that returns a pending external tool call
- **THEN** the provider step SHALL be invoked once
- **AND** the pending tool call SHALL appear once in the final response messages
