## ADDED Requirements

### Requirement: Text generation requests expose lifecycle controls
The system SHALL allow text-generation callers to configure lifecycle callbacks, timeout, cancellation, metadata, and context through provider-neutral request fields.

#### Scenario: Java caller passes lifecycle controls
- **WHEN** a Java caller builds `GenerateTextRequest` with lifecycle callbacks, timeout, cancellation token, metadata, or context
- **THEN** the language model service MUST apply those controls during `generateText` and `streamText`
- **AND** public request types MUST remain independent from Spring AI and provider SDK classes

#### Scenario: Lifecycle controls do not enter provider prompt
- **WHEN** lifecycle metadata or context is attached to a generation request
- **THEN** the system MUST expose that data to lifecycle events
- **AND** it MUST NOT convert lifecycle metadata or context into model prompt messages unless a future explicit feature requests it

### Requirement: Embedding requests expose lifecycle controls
The system SHALL allow advanced embedding calls to configure lifecycle callbacks, timeout, cancellation, metadata, and context through provider-neutral request fields.

#### Scenario: Embedding start and finish callbacks
- **WHEN** a caller invokes an embedding request with lifecycle callbacks
- **THEN** the system MUST invoke embedding start before provider invocation and embedding finish after embeddings are produced

#### Scenario: Embedding timeout and cancellation
- **WHEN** an embedding timeout expires or cancellation is requested
- **THEN** the embedding call MUST fail with the corresponding typed timeout or cancellation error

### Requirement: Lifecycle errors use typed safe exceptions
The public API SHALL expose timeout and cancellation failures as typed safe AI Foundation exceptions.

#### Scenario: Timeout exception
- **WHEN** a generation or embedding call times out
- **THEN** the raised exception MUST identify timeout scope and contain a safe message

#### Scenario: Cancellation exception
- **WHEN** a generation or embedding call is cancelled
- **THEN** the raised exception MUST identify cancellation and contain a safe message

#### Scenario: Provider error remains distinct
- **WHEN** a provider API call fails independently of timeout or cancellation
- **THEN** the system MUST preserve provider error classification rather than reporting it as cancellation
