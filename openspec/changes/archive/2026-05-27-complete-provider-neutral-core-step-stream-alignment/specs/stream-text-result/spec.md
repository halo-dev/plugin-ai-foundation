## ADDED Requirements

### Requirement: Stream result exposes final convenience projections
The system SHALL expose final-result convenience projections from `StreamTextResult` for text, reasoning text, usage, total usage, finish reason, raw finish reason, warnings, steps, tool calls, tool results, tool errors, request metadata, response metadata, provider metadata, content, reasoning parts, and structured output.

#### Scenario: Caller reads final text directly
- **WHEN** a caller subscribes to `StreamTextResult.text()`
- **THEN** the system MUST resolve the complete generated answer text after the stream finishes

#### Scenario: Caller reads final usage directly
- **WHEN** a caller subscribes to `StreamTextResult.totalUsage()`
- **THEN** the system MUST resolve aggregate usage across all completed steps

#### Scenario: Caller reads tool results directly
- **WHEN** a multi-step stream executes tools
- **THEN** the system MUST expose the final step tool calls, final step tool results, and aggregated step details through convenience projections

### Requirement: Stream result projections share one execution
The system SHALL ensure `fullStream`, `textStream`, `partialOutputStream`, `elementStream`, final output projections, and final result projections share one underlying provider execution.

#### Scenario: Multiple subscribers consume projections
- **WHEN** a caller consumes `textStream()`, `fullStream()`, and `result()` from the same `StreamTextResult`
- **THEN** the system MUST invoke the provider only once

#### Scenario: Late final result subscriber
- **WHEN** a caller subscribes to a final projection after the full stream has completed
- **THEN** the system MUST return the cached final result without invoking the provider again

### Requirement: Stream projection errors follow stream semantics
The system SHALL surface provider and validation errors consistently across full stream and final projections.

#### Scenario: Full stream emits error part
- **WHEN** the provider fails during streaming
- **THEN** `fullStream()` MUST emit an `error` part containing sanitized error information

#### Scenario: Final projection observes failure
- **WHEN** the provider fails during streaming
- **THEN** final projections MUST terminate with the corresponding sanitized generation exception
