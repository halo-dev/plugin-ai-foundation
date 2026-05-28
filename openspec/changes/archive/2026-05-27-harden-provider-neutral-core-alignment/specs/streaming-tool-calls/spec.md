## ADDED Requirements

### Requirement: Streaming tool calls through StreamTextResult
The streaming tool-call loop SHALL remain progressive when exposed through `StreamTextResult`.

#### Scenario: Full stream preserves tool order
- **WHEN** a `StreamTextResult.fullStream()` includes streamed tool execution
- **THEN** `tool-call` parts SHALL be emitted before their corresponding `tool-result` or `tool-error` parts
- **AND** later answer text from continuation steps SHALL be emitted after the relevant tool result history is appended

#### Scenario: Text stream excludes tool events
- **WHEN** a `StreamTextResult.textStream()` is consumed for a tool-enabled generation
- **THEN** it SHALL emit only answer text deltas from provider steps
- **AND** it SHALL NOT emit serialized tool calls, tool results, or tool errors as answer text

#### Scenario: Tool execution is not duplicated
- **WHEN** both `fullStream()` and `textStream()` are consumed from the same `StreamTextResult`
- **THEN** each server-side tool call SHALL be executed at most once
