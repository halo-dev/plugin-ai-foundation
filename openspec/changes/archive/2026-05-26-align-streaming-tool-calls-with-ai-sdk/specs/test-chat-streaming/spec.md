## ADDED Requirements

### Requirement: Workbench observes streamed tool progress
The Console model test workbench SHALL show tool-enabled streams progressively instead of waiting for the final assistant answer.

#### Scenario: Tool call appears before final answer
- **WHEN** a test-chat stream emits a `tool-call` part
- **THEN** the workbench SHALL record or render the tool activity on the active assistant message
- **AND** it SHALL keep the assistant response in progress until `finish`, `error`, abort, or `[DONE]`

#### Scenario: Tool result appears during stream
- **WHEN** a test-chat stream emits a `tool-result` part
- **THEN** the workbench SHALL record or render the result activity without appending it to the assistant answer text
- **AND** subsequent text deltas from later provider steps SHALL append to the same active assistant answer

#### Scenario: Tool stream remains live during execution
- **WHEN** a tool-enabled response pauses while a server-side tool executor runs
- **THEN** the workbench SHALL keep the active assistant message in an in-progress state
- **AND** it SHALL NOT treat the pause after `tool-call` as stream completion
