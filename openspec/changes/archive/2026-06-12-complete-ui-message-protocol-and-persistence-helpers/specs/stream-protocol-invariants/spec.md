## ADDED Requirements

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
