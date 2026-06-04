## ADDED Requirements

### Requirement: Tool execution is Reactor-native
The streaming and non-streaming tool loops SHALL compose server-side tool executor and tool-call repair `Mono` values without calling `block()`, `blockFirst()`, or `blockLast()` on Reactor non-blocking threads.

#### Scenario: Streamed tool uses Halo reactive APIs
- **WHEN** a caller invokes `LanguageModel.streamText(request)` with a server-side tool whose executor returns a `Mono` backed by Halo reactive APIs such as `ReactiveExtensionClient.list`
- **THEN** the tool executor `Mono` SHALL be subscribed as part of the generation stream without blocking the caller thread
- **AND** the stream SHALL emit the corresponding `tool-result` after the executor completes
- **AND** the stream SHALL NOT fail with Reactor's non-blocking-thread blocking error

#### Scenario: Tool repair uses Halo reactive APIs
- **WHEN** a streamed provider step returns invalid tool input
- **AND** the request has a tool-call repair callback that returns a `Mono` backed by Halo reactive APIs
- **THEN** the repair callback `Mono` SHALL be composed without blocking the caller thread
- **AND** repaired valid input SHALL be used for the tool executor according to the existing repair semantics

#### Scenario: Tool execution remains ordered
- **WHEN** a provider step returns multiple server-side tool calls
- **THEN** the system MUST execute and record tool calls in deterministic provider order
- **AND** it MUST preserve the existing behavior for stopping after a tool error, pending external tool, or approval request

#### Scenario: Tool timeout remains scoped to one executor
- **WHEN** a server-side tool executor does not complete before the configured tool timeout
- **THEN** the system MUST cancel or terminate that executor chain where Reactor cancellation can be observed
- **AND** the current step MUST record a safe tool error
- **AND** the timeout MUST NOT incorrectly apply to unrelated provider steps or later tool calls

