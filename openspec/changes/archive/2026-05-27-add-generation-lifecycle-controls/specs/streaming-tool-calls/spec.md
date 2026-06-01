## ADDED Requirements

### Requirement: Tool execution observes lifecycle callbacks
The streaming and non-streaming tool loops SHALL invoke tool lifecycle callbacks around server-side tool execution.

#### Scenario: Streamed tool start callback order
- **WHEN** a streamed step finishes with an executable tool call
- **THEN** the system MUST emit the `tool-call` stream part before invoking the tool-call-start callback
- **AND** the callback MUST be invoked before the executor starts

#### Scenario: Streamed tool finish callback order
- **WHEN** a streamed tool executor completes successfully
- **THEN** the system MUST invoke tool-call-finish before emitting the corresponding `tool-result` part

#### Scenario: Streamed tool failure callback order
- **WHEN** a streamed tool executor fails
- **THEN** the system MUST invoke tool-call-finish with the safe tool error before emitting the corresponding `tool-error` part

### Requirement: Tool execution respects timeout and cancellation
The system SHALL apply configured tool timeout and cancellation token to server-side tool execution.

#### Scenario: Tool timeout creates tool error
- **WHEN** a server-side tool executor exceeds the configured tool timeout
- **THEN** the current step MUST record a safe tool error
- **AND** the tool loop MUST stop unless future policy explicitly allows continuing after tool timeout

#### Scenario: Cancellation before tool execution
- **WHEN** cancellation is requested after a tool call is emitted but before the executor starts
- **THEN** the system MUST NOT invoke the executor
- **AND** the stream MUST terminate with cancellation semantics

#### Scenario: Cancellation during tool execution
- **WHEN** cancellation is requested while a tool executor `Mono` is running
- **THEN** the system MUST cancel the executor subscription where Reactor cancellation can be observed
- **AND** the generation MUST surface typed cancellation semantics
