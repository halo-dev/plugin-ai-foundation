## ADDED Requirements

### Requirement: Workbench handles reasoning stream parts
The model test workbench SHALL consume reasoning stream parts without mixing reasoning into assistant answer text.

#### Scenario: Reasoning deltas are received
- **WHEN** the workbench receives reasoning stream parts for the active assistant message
- **THEN** it SHALL store or render the reasoning separately from the assistant answer content
- **AND** it SHALL NOT append reasoning text to the Markdown-rendered answer body

#### Scenario: Reasoning display is optional
- **WHEN** the active response contains reasoning
- **THEN** the workbench MAY show a compact reasoning section associated with the assistant message
- **AND** the absence of a visible reasoning section SHALL NOT break streaming or answer rendering

#### Scenario: Unknown reasoning metadata
- **WHEN** a reasoning part contains provider metadata unknown to the frontend
- **THEN** the workbench SHALL ignore the unknown metadata for rendering
- **AND** the stream SHALL continue until `finish`, `error`, abort, or `[DONE]`
