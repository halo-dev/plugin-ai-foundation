## ADDED Requirements

### Requirement: Documentation Covers UI Message Cancellation Contract
Consumer documentation SHALL explain how plugin authors wire UI message chat cancellation.

#### Scenario: Cancellation helper usage is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows creating a UI message cancellation helper
- **AND** passing its token to `UIMessageChatOptions`

#### Scenario: Subscriber cancellation binding is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows wrapping the response body with a subscriber-cancel binding helper
- **AND** it explains that the helper cancels only on subscriber cancellation

#### Scenario: WebFlux remains manual glue
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide shows WebFlux-style manual request and response wiring
- **AND** it states that the SDK does not provide a WebFlux adapter

### Requirement: Documentation Covers Cancellation Finish Semantics
Consumer documentation SHALL explain what callers receive when cancellation aborts a UI message stream.

#### Scenario: Abort is distinguished from error
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide explains that expected cancellation maps to an `abort` chunk rather than an `error` chunk

#### Scenario: Partial message persistence decision is documented
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide explains that `onFinish` still receives a partial response message
- **AND** it explains that callers decide whether to persist the aborted partial message

#### Scenario: Error text boundary is documented
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide explains that expected cancellation does not produce safe error text

### Requirement: Documentation Tracks Deferred Cancellation Work
Consumer documentation SHALL record cancellation-adjacent work that remains intentionally out of scope.

#### Scenario: Stop endpoint is deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that stop endpoints require future active stream registry work

#### Scenario: Resume and reconnect remain deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that resume, reconnect, replay, and stream id behavior remain future work

#### Scenario: Cancellation reason is deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that this version does not expose structured cancellation reasons

#### Scenario: Frontend helper is deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that npm helper behavior remains future work
