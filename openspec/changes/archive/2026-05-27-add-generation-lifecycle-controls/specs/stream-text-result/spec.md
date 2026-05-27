## ADDED Requirements

### Requirement: Stream result exposes cancellation consistently
The system SHALL make cancellation visible through stream protocol events and final result projections.

#### Scenario: Cancellation during full stream
- **WHEN** a stream is cancelled through a request cancellation token after `start` has emitted
- **THEN** `fullStream()` MUST close any open lifecycle block and emit a terminal abort or error part

#### Scenario: Cancellation during final projection
- **WHEN** a caller consumes `result()` after the stream was cancelled
- **THEN** the final projection MUST fail with the typed cancellation exception

### Requirement: Stream result exposes timeout consistently
The system SHALL make timeout visible through stream protocol events and final result projections.

#### Scenario: Timeout during full stream
- **WHEN** a stream times out after `start` has emitted
- **THEN** `fullStream()` MUST emit a safe error part that identifies timeout without leaking provider secrets

#### Scenario: Timeout during final projection
- **WHEN** a caller consumes `result()` after timeout
- **THEN** the final projection MUST fail with the typed timeout exception

### Requirement: Stream callbacks follow shared execution semantics
Lifecycle callbacks attached to a streaming request SHALL observe the same shared execution used by all stream projections.

#### Scenario: Multiple stream views
- **WHEN** a caller consumes `fullStream()`, `textStream()`, and `result()` from one stream result
- **THEN** lifecycle callbacks MUST NOT be invoked more than once for the same generation, step, or tool execution
