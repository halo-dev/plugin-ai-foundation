## ADDED Requirements

### Requirement: Streaming Tool Calls Emit Approval Requests
The streaming tool-call loop SHALL expose approval requests as Halo-owned stream parts.

#### Scenario: Streamed tool call requires approval
- **WHEN** a streamed provider step finishes with a valid tool call that requires approval
- **THEN** the stream SHALL emit the completed `tool-call` part
- **AND** it SHALL emit a `tool-approval-request` part for that call before the step finish event
- **AND** it SHALL NOT execute the tool during the same request

#### Scenario: Text stream excludes approval events
- **WHEN** `StreamTextResult.textStream()` is consumed for a generation that includes approval requests
- **THEN** it SHALL emit only answer text deltas
- **AND** it SHALL NOT emit serialized approval requests or responses as answer text

### Requirement: Streaming Approval Resumption Uses Message History
Streaming generation SHALL resume approved and denied tool calls from supplied messages before starting the next provider stream.

#### Scenario: Approved pending tool resumes stream
- **WHEN** `LanguageModel.streamText(request)` receives messages containing a pending approval request and matching approved response
- **THEN** the system SHALL execute the tool before invoking the provider stream
- **AND** `fullStream()` SHALL emit the resulting tool execution part before later answer text from the provider continuation

#### Scenario: Denied pending tool resumes stream
- **WHEN** `LanguageModel.streamText(request)` receives messages containing a pending approval request and matching denied response
- **THEN** the system SHALL not invoke the tool executor
- **AND** `fullStream()` SHALL emit a safe denial tool error or denial result before later answer text from the provider continuation
