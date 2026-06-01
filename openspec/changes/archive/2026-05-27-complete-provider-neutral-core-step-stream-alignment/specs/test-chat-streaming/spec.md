## ADDED Requirements

### Requirement: Console test endpoint streams canonical full-stream parts
The console model test streaming endpoint SHALL serialize the canonical `StreamTextResult.fullStream()` event order without reordering text, reasoning, tool, source, file, finish, or error parts.

#### Scenario: Reasoning appears before answer text
- **WHEN** the model emits reasoning before final answer text
- **THEN** the endpoint MUST send reasoning parts before text parts in the same order as `fullStream()`

#### Scenario: Tool call appears before final answer
- **WHEN** a stream executes a tool before the final answer
- **THEN** the endpoint MUST send tool call and tool result parts before the final answer text parts that depend on them

#### Scenario: Stream terminates
- **WHEN** `fullStream()` completes
- **THEN** the endpoint MUST send the terminal `[DONE]` marker after the final full-stream part

### Requirement: Console parser accepts new stream event types
The console test UI SHALL parse source, file, tool-input-start, and tool-input-delta event types without breaking existing text, reasoning, and tool rendering.

#### Scenario: Unknown future event
- **WHEN** the UI receives a future event type not yet rendered by the workbench
- **THEN** the parser MUST preserve stream processing and ignore the unknown event for display

#### Scenario: Tool input delta event
- **WHEN** the UI receives tool input delta events before a tool call
- **THEN** the parser MUST keep the final tool call display associated with the same tool call id
