## ADDED Requirements

### Requirement: Stream lifecycle blocks do not overlap
The system SHALL enforce non-overlapping lifecycle blocks for text, reasoning, and tool-input stream parts.

#### Scenario: Reasoning before text
- **WHEN** a provider emits reasoning deltas followed by answer text deltas
- **THEN** the full stream MUST emit `reasoning-start`, `reasoning-delta`, `reasoning-end`, `text-start`, `text-delta`, and `text-end` in non-overlapping order

#### Scenario: Text before reasoning
- **WHEN** a provider emits answer text deltas followed by reasoning deltas
- **THEN** the full stream MUST close the text block before opening the reasoning block

#### Scenario: Tool input before final tool call
- **WHEN** a provider emits incremental tool input for a tool call
- **THEN** the full stream MUST close the tool-input block before emitting the final `tool-call` event for that call

### Requirement: Stream starts and finishes exactly once
The system SHALL emit exactly one stream `start` part before step events and exactly one stream `finish` part after all step events unless the stream aborts before a provider request starts.

#### Scenario: Successful stream
- **WHEN** streaming generation completes successfully
- **THEN** the full stream MUST contain one `start` event before the first `start-step` and one `finish` event after the final `finish-step`

#### Scenario: Provider error after stream start
- **WHEN** the provider fails after the stream has started
- **THEN** the full stream MUST emit an `error` event and close any open lifecycle block before terminating

### Requirement: Step lifecycle surrounds step parts
The system SHALL emit `start-step` before any provider-derived content for that step and `finish-step` after all content, tool, source, file, and error parts for that step have been emitted.

#### Scenario: Tool step ordering
- **WHEN** a step produces text, tool calls, and tool results
- **THEN** all parts for that step MUST appear between the matching `start-step` and `finish-step`

#### Scenario: Multiple steps
- **WHEN** a generation executes multiple model steps
- **THEN** each step MUST have its own ordered `start-step` and `finish-step` pair with increasing zero-based step indexes

### Requirement: Protocol validation is reusable
The system SHALL provide a reusable internal validator or assertion utility for full-stream protocol tests.

#### Scenario: Test detects overlapping blocks
- **WHEN** a test stream opens reasoning before closing an active text block
- **THEN** the validator MUST fail that stream as invalid

#### Scenario: Test accepts separated blocks
- **WHEN** a test stream closes text before opening reasoning
- **THEN** the validator MUST accept the stream as valid
