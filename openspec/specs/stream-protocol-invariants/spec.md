# stream-protocol-invariants Specification

## Purpose

Define canonical lifecycle ordering for Halo full-stream text generation parts.
## Requirements
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

### Requirement: Stream Parts Are Type-Safe And Protocol-Compatible
Stream part APIs SHALL use Java-oriented abstractions or factories for each stream part kind while preserving the provider-neutral lifecycle protocol.

#### Scenario: Text stream lifecycle
- **WHEN** a provider emits text deltas
- **THEN** the SDK emits typed text-start, text-delta, and text-end parts without nesting unrelated part kinds inside the text lifecycle

#### Scenario: Reasoning stream lifecycle
- **WHEN** a provider emits reasoning deltas
- **THEN** the SDK emits typed reasoning-start, reasoning-delta, and reasoning-end parts as an independent lifecycle from text

### Requirement: Invalid Stream Part Shapes Are Prevented
The SDK SHALL prevent stream parts from carrying fields that are invalid for their part kind.

#### Scenario: Caller creates a stream part
- **WHEN** code constructs a stream part through public APIs
- **THEN** the construction path exposes only fields valid for that specific part kind

#### Scenario: Provider mapping emits invalid data
- **WHEN** provider mapping attempts to create a stream part with an invalid shape
- **THEN** stream validation fails before the invalid part is exposed to SDK callers

### Requirement: UI message stream preserves step starts
The UI message stream projection SHALL preserve step start lifecycle events while keeping accumulated UI messages free of lifecycle-only parts.

#### Scenario: Start step appears before step content
- **WHEN** a full stream contains `start-step` before provider-derived content
- **THEN** the UI message stream projection SHALL emit `start-step` before the corresponding UI content chunks

#### Scenario: Start step pairs with finish step
- **WHEN** a full stream contains a `start-step` and matching `finish-step`
- **THEN** the UI message stream projection SHALL preserve both lifecycle chunks in order
- **AND** the npm reducer SHALL NOT add either lifecycle chunk as a visible message part

### Requirement: UI tool chunks follow canonical lifecycle order
The UI message stream projection SHALL preserve tool lifecycle ordering through canonical tool chunks.

#### Scenario: Tool input order is preserved
- **WHEN** a full stream emits `tool-input-start`, `tool-input-delta`, and completed `tool-call` for one tool call
- **THEN** the UI message stream SHALL emit `tool-input-start`, `tool-input-delta`, and `tool-input-available` in that order for the same tool call id

#### Scenario: Tool output follows input availability
- **WHEN** a full stream emits a `tool-result` or `tool-error` for a tool call
- **THEN** the UI message stream SHALL emit the matching output chunk after the input-available chunk for that tool call when both are present in the same stream

