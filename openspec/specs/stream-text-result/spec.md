## Purpose

Define the provider-neutral rich result returned by streaming text generation.
## Requirements
### Requirement: Stream text result contract
The system SHALL expose streaming text generation as a provider-neutral result object with multiple views over one generation.

#### Scenario: Full stream view
- **WHEN** a consumer calls `languageModel.streamText(request)`
- **THEN** the system SHALL return a `StreamTextResult`
- **AND** `StreamTextResult.fullStream()` SHALL emit Halo `TextStreamPart` lifecycle, text, reasoning, tool, raw, finish, abort, and error parts

#### Scenario: Text-only stream view
- **WHEN** a consumer subscribes to `StreamTextResult.textStream()`
- **THEN** the stream SHALL emit only generated answer text deltas in order
- **AND** it SHALL NOT emit reasoning, tool, raw, finish, or error lifecycle objects as text

#### Scenario: Stream blocks do not overlap
- **WHEN** `StreamTextResult.fullStream()` emits block lifecycle parts such as `text-start`/`text-end` or `reasoning-start`/`reasoning-end`
- **THEN** each block SHALL be closed before another block type is opened
- **AND** the stream SHALL NOT emit crossing sequences such as `text-start`, `reasoning-start`, `reasoning-end`, `text-end`

#### Scenario: Single generation execution
- **WHEN** a consumer subscribes to multiple views from the same `StreamTextResult`
- **THEN** the provider SHALL be invoked at most once for each generation step
- **AND** server-side tools SHALL be executed at most once for each tool call

#### Scenario: Final result access
- **WHEN** a streamed generation completes successfully
- **THEN** `StreamTextResult.result()` SHALL complete with the same model-independent fields available from `GenerateTextResult`
- **AND** `StreamTextResult.output()` SHALL complete with the parsed structured output when structured output was requested

#### Scenario: Caller reads final text directly
- **WHEN** a caller subscribes to `StreamTextResult.text()`
- **THEN** the system MUST resolve the complete generated answer text after the stream finishes

#### Scenario: Caller reads final usage directly
- **WHEN** a caller subscribes to `StreamTextResult.totalUsage()`
- **THEN** the system MUST resolve aggregate usage across all completed steps

#### Scenario: Caller reads tool results directly
- **WHEN** a multi-step stream executes tools
- **THEN** the system MUST expose the final step tool calls, final step tool results, and aggregated step details through convenience projections

#### Scenario: Late final result subscriber
- **WHEN** a caller subscribes to a final projection after the full stream has completed
- **THEN** the system MUST return the cached final result without invoking the provider again

#### Scenario: Final stream error access
- **WHEN** a streamed generation fails before a valid final result is available
- **THEN** `StreamTextResult.fullStream()` SHALL emit a safe `error` part when the protocol can still complete gracefully
- **AND** `StreamTextResult.result()` and `StreamTextResult.output()` SHALL fail with the typed cause when applicable

#### Scenario: Final projection observes failure
- **WHEN** the provider fails during streaming
- **THEN** final projections MUST terminate with the corresponding sanitized generation exception

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
