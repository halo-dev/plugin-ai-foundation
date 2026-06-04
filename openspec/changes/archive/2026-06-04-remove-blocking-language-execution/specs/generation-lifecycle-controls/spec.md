## MODIFIED Requirements

### Requirement: Lifecycle callbacks are safe observers
Lifecycle callbacks SHALL NOT trigger additional provider calls, duplicate server-side tool execution, or block Reactor non-blocking threads.

#### Scenario: Multiple stream projections with callbacks
- **WHEN** a caller consumes multiple projections from one `StreamTextResult`
- **THEN** lifecycle callbacks MUST be invoked for the single shared generation execution only

#### Scenario: Callback failure is captured
- **WHEN** a lifecycle callback fails
- **THEN** the generation MUST continue when possible
- **AND** the failure MUST be surfaced as a provider-neutral warning with a safe message

#### Scenario: Callback uses Halo reactive APIs
- **WHEN** a streaming generation lifecycle callback returns a `Mono` backed by Halo reactive APIs such as `ReactiveExtensionClient.list`
- **THEN** the callback `Mono` SHALL be composed as part of the generation chain without calling `block()`, `blockFirst()`, or `blockLast()` on Reactor non-blocking threads
- **AND** lifecycle event ordering MUST remain consistent with the existing callback order requirements

#### Scenario: Tool lifecycle callback uses Halo reactive APIs
- **WHEN** a tool-call-start or tool-call-finish callback returns a `Mono` backed by Halo reactive APIs
- **THEN** the callback `Mono` SHALL complete before the next lifecycle-dependent tool action or stream part is emitted
- **AND** the generation SHALL NOT fail with Reactor's non-blocking-thread blocking error

