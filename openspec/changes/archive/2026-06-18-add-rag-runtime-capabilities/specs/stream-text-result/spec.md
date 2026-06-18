## ADDED Requirements

### Requirement: Stream result exposes sources
`StreamTextResult` SHALL expose source projections derived from the full stream and final result.

#### Scenario: Read source references
- **WHEN** a stream emits source parts
- **THEN** callers can read source references without manually filtering raw stream parts

#### Scenario: Source projection shares execution
- **WHEN** callers consume text and sources from the same `StreamTextResult`
- **THEN** the underlying provider stream is not executed more than once
