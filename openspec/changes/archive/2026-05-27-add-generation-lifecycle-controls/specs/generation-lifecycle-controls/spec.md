## ADDED Requirements

### Requirement: Generation requests expose lifecycle callbacks
The system SHALL allow Java SDK callers to attach provider-neutral lifecycle callbacks to generation requests without exposing provider implementation classes.

#### Scenario: Generation start callback
- **WHEN** a caller sends a request with a lifecycle callback
- **THEN** the system MUST invoke the start callback before the first provider call
- **AND** the event MUST include provider-neutral request metadata, caller metadata, and caller context when available

#### Scenario: Generation finish callback
- **WHEN** generation completes successfully
- **THEN** the system MUST invoke the finish callback after the final step has completed
- **AND** the event MUST include final text, finish reason, warnings, total usage, steps, tool calls, tool results, tool errors, and provider metadata when available

#### Scenario: Generation error callback
- **WHEN** generation fails before a successful final result is available
- **THEN** the system MUST invoke the error callback with a safe typed error and caller metadata

### Requirement: Step lifecycle callbacks observe every model step
The system SHALL emit lifecycle callbacks around each provider model step in both streaming and non-streaming generation.

#### Scenario: Step start callback
- **WHEN** a generation step is about to invoke the provider
- **THEN** the system MUST invoke the step-start callback with step index, messages, tools, active tools, tool choice, provider options, timeout settings, and previous steps

#### Scenario: Step finish callback
- **WHEN** a provider step completes
- **THEN** the system MUST invoke the step-finish callback with the completed `GenerationStep`
- **AND** multi-step generation MUST invoke step-finish once per completed provider step

#### Scenario: Step callback order
- **WHEN** generation executes multiple steps
- **THEN** the callback order MUST be start, step-start, step-finish for each step, and finish

### Requirement: Tool lifecycle callbacks observe server-side tool execution
The system SHALL emit lifecycle callbacks around server-side tool executor calls.

#### Scenario: Tool call start callback
- **WHEN** the system is about to execute a server-side tool
- **THEN** the system MUST invoke the tool-call-start callback with tool call id, tool name, parsed input, step index, and provider metadata

#### Scenario: Tool call finish callback succeeds
- **WHEN** a server-side tool executor returns successfully
- **THEN** the system MUST invoke the tool-call-finish callback with the corresponding tool result and duration metadata

#### Scenario: Tool call finish callback fails
- **WHEN** a server-side tool executor fails
- **THEN** the system MUST invoke the tool-call-finish callback with the corresponding safe tool error and duration metadata

### Requirement: Lifecycle callbacks are safe observers
Lifecycle callbacks SHALL NOT trigger additional provider calls or duplicate server-side tool execution.

#### Scenario: Multiple stream projections with callbacks
- **WHEN** a caller consumes multiple projections from one `StreamTextResult`
- **THEN** lifecycle callbacks MUST be invoked for the single shared generation execution only

#### Scenario: Callback failure is captured
- **WHEN** a lifecycle callback fails
- **THEN** the generation MUST continue when possible
- **AND** the failure MUST be surfaced as a provider-neutral warning with a safe message

### Requirement: Generation calls support timeout controls
The system SHALL allow callers to set provider-neutral timeout controls for total generation, individual provider steps, and server-side tool execution.

#### Scenario: Total timeout expires
- **WHEN** the total generation timeout expires before generation completes
- **THEN** the system MUST stop the generation and surface a typed timeout error

#### Scenario: Step timeout expires
- **WHEN** a provider step exceeds the configured step timeout
- **THEN** the system MUST stop that step and surface a typed timeout error

#### Scenario: Tool timeout expires
- **WHEN** a server-side tool executor exceeds the configured tool timeout
- **THEN** the system MUST record a tool error and stop the tool loop unless caller policy later defines otherwise

### Requirement: Generation calls support cancellation controls
The system SHALL allow callers to pass a provider-neutral cancellation token to generation and embedding requests.

#### Scenario: Cancel before provider call
- **WHEN** the cancellation token is already cancelled before the first provider call
- **THEN** the system MUST fail the request with a typed cancellation error without invoking the provider

#### Scenario: Cancel during streamed generation
- **WHEN** the cancellation token is cancelled after a stream has started
- **THEN** `fullStream()` MUST emit a terminal abort or error part with a safe cancellation message
- **AND** final projections MUST fail with a typed cancellation error

#### Scenario: Reactor subscriber cancellation
- **WHEN** a caller cancels the Reactor subscription for `fullStream()`
- **THEN** the system MUST stop downstream generation work where Reactor cancellation can be observed

### Requirement: Lifecycle fields remain Java-only where required
The system SHALL keep callback and cancellation objects out of HTTP/OpenAPI schemas.

#### Scenario: API client generation
- **WHEN** OpenAPI docs and TypeScript clients are generated
- **THEN** Java-only lifecycle callback and cancellation fields MUST NOT appear in generated HTTP request models

#### Scenario: Serializable timeout and metadata fields
- **WHEN** timeout or metadata fields are designed to be serializable
- **THEN** they MAY appear in generated HTTP request models only if they contain no Java callback or runtime object references
