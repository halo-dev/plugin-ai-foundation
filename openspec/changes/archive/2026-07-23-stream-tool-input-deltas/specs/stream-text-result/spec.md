## ADDED Requirements

### Requirement: Full stream exposes complete tool input lifecycle
`StreamTextResult.fullStream()` SHALL expose real provider-native incremental tool input as typed lifecycle parts.

#### Scenario: Incremental tool input completes
- **WHEN** a provider emits reliable incremental input for a named tool call
- **THEN** `fullStream()` MUST emit `tool-input-start`, ordered `tool-input-delta` parts, `tool-input-end`, and the authoritative completed `tool-call`

#### Scenario: Incremental tool input fails validation
- **WHEN** the final accumulated input for a known tool cannot be validated or repaired
- **THEN** `fullStream()` SHALL emit `tool-input-end` followed by `tool-input-error`
- **AND** it SHALL NOT emit an authoritative completed `tool-call` for that invalid input

#### Scenario: Provider is final-only
- **WHEN** the provider exposes no reliable incremental fragments
- **THEN** `fullStream()` SHALL emit only the authoritative completed `tool-call` or its input error
- **AND** it SHALL NOT fabricate start, delta, or end parts

### Requirement: Stream projections use cancellable single-run replay
All projections of one `StreamTextResult` SHALL share one lazy generation execution whose cancellation and terminal state are replayable.

#### Scenario: Multiple projections share one run
- **WHEN** callers subscribe to multiple projections from one result
- **THEN** the provider, repair callback, tool input callback, approval decision, and server executor SHALL each run at most once for the corresponding event

#### Scenario: No subscriber means no execution
- **WHEN** no projection has been subscribed
- **THEN** the provider generation SHALL NOT start

#### Scenario: One subscriber remains
- **WHEN** one projection cancels while another projection remains subscribed
- **THEN** the shared provider generation SHALL remain active for the remaining subscriber

#### Scenario: Last subscriber cancels
- **WHEN** the final active projection subscriber cancels before completion
- **THEN** the coordinator SHALL cancel the provider subscription and any active tool input callback
- **AND** it SHALL record a typed cancellation terminal state

#### Scenario: Late subscriber after cancellation
- **WHEN** a projection subscribes after last-subscriber cancellation
- **THEN** it SHALL observe the already produced history and the recorded cancellation outcome
- **AND** it SHALL NOT invoke the provider again

#### Scenario: Late subscriber after completion or failure
- **WHEN** a projection subscribes after normal completion or failure
- **THEN** it SHALL receive the replayed terminal result or typed failure
- **AND** it SHALL NOT invoke the provider again
