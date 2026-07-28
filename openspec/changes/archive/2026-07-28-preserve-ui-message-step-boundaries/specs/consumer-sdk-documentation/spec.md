## ADDED Requirements

### Requirement: UI message documentation explains persisted step boundaries
The developer UI-message guide SHALL document the caller-visible generation-step persistence and reuse contract without internal implementation detail.

#### Scenario: Persisted marker is documented
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain that stream `start-step` chunks accumulate into marker-only `step-start` parts
- **AND** it SHALL explain that callers must preserve ordered message parts when storing assistant messages

#### Scenario: Conversion grouping is documented
- **WHEN** a plugin author reads the UI message conversion section
- **THEN** the guide SHALL explain that conversion groups reasoning and multiple tool calls by generation step
- **AND** it SHALL explain that messages without a marker are treated as one implicit step

#### Scenario: Internal details are excluded
- **WHEN** the developer guide describes step-boundary reuse
- **THEN** it SHALL NOT include reducer internals, incident-specific failure analysis, or runtime capability-resolution algorithms
