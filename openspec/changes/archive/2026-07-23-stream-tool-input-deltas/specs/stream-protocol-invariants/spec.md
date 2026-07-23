## MODIFIED Requirements

### Requirement: UI tool chunks follow canonical lifecycle order
The UI message stream projection SHALL preserve canonical tool lifecycle ordering while omitting backend-only input-end events.

#### Scenario: Tool input order is preserved
- **WHEN** a full stream emits `tool-input-start`, `tool-input-delta`, `tool-input-end`, and completed `tool-call` for one tool call
- **THEN** the UI message stream SHALL emit `tool-input-start`, `tool-input-delta`, and `tool-input-available` in that order for the same stable tool call id
- **AND** it SHALL NOT emit a `tool-input-end` UI chunk

#### Scenario: Tool input error follows streaming input
- **WHEN** a full stream ends a tool-input block and then emits `tool-input-error`
- **THEN** the UI message stream SHALL emit the corresponding canonical input-error chunk after all input deltas
- **AND** it SHALL NOT emit input availability for that call

#### Scenario: Tool output follows input availability
- **WHEN** a full stream emits a `tool-result` or `tool-error` for a tool call
- **THEN** the UI message stream SHALL emit the matching output chunk after the input-available chunk for that tool call when both are present in the same stream

## ADDED Requirements

### Requirement: Tool input lifecycle is ordered per stable call identity
The full stream SHALL preserve a non-overlapping input lifecycle for each stable tool call id while allowing provider calls to be interleaved internally.

#### Scenario: Successful incremental lifecycle
- **WHEN** a provider emits reliable fragments for one known tool call
- **THEN** the public sequence MUST be start, zero or more ordered deltas, end, and completed tool call
- **AND** no delta for that id SHALL occur before start or after end

#### Scenario: Failed incremental lifecycle
- **WHEN** a known incremental call fails final validation or repair
- **THEN** the public sequence MUST end the input block before emitting `tool-input-error`
- **AND** it MUST NOT emit a completed tool call for that invalid input

#### Scenario: Final-only lifecycle
- **WHEN** a provider exposes only final input
- **THEN** a completed tool call or tool input error MAY appear without synthetic start, delta, or end parts

#### Scenario: Open block on provider failure
- **WHEN** the provider fails while a tool-input block is open
- **THEN** the full stream SHALL close the open block before the terminal stream error
- **AND** it SHALL NOT fabricate final input availability
