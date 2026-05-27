## Purpose

Define server-side tool calling behavior for progressive `LanguageModel.streamText` responses.

## Requirements

### Requirement: Progressive streaming tool execution
The system SHALL keep language model streaming progressive when request-scoped server-side tools are present.

#### Scenario: Tool-enabled stream emits provider deltas before completion
- **WHEN** a caller invokes `LanguageModel.streamText(request)` with tools
- **AND** the provider emits text or reasoning deltas before the step finishes
- **THEN** the returned Flux SHALL emit corresponding stream parts before tool execution and before final completion
- **AND** the implementation SHALL NOT wait for all generation steps to finish before emitting the first model delta

#### Scenario: Completed tool call is emitted before execution result
- **WHEN** a streamed provider step finishes with a tool call
- **THEN** the stream SHALL emit a `tool-call` part containing the tool call id, tool name, and parsed input
- **AND** the `tool-call` part SHALL be emitted before the corresponding `tool-result` or `tool-error`

#### Scenario: Server-side tool result continues generation
- **WHEN** a streamed step returns an executable tool call
- **AND** `stopWhen` allows another provider step
- **THEN** the system SHALL execute the tool server-side
- **AND** emit a `tool-result` part after the executor completes
- **AND** append assistant tool-call history plus tool result history before starting the next streamed provider step

### Requirement: Multi-step stream lifecycle
The system SHALL expose each streamed provider call as a separate generation step.

#### Scenario: Multiple streamed steps
- **WHEN** tool execution causes generation to continue across multiple provider calls
- **THEN** the stream SHALL emit `start-step` and `finish-step` for each provider step using zero-based step indexes
- **AND** the final `finish` part SHALL be emitted only after the last streamed step completes or the loop stops

#### Scenario: Stop condition reached with tool call
- **WHEN** a streamed step returns a tool call
- **AND** the configured stop condition does not allow another provider step
- **THEN** the stream SHALL emit the `tool-call` part
- **AND** the `finish-step` part SHALL include a warning that the stop condition was reached
- **AND** the system SHALL NOT execute the tool

#### Scenario: Tool execution failure stops stream loop
- **WHEN** a server-side tool executor fails during a streamed tool loop
- **THEN** the stream SHALL emit a `tool-error` part with a safe error message
- **AND** emit a `finish-step` part for the current step
- **AND** emit a final `finish` part without starting another provider step

### Requirement: Reasoning-aware streamed tool continuation
The system SHALL preserve reasoning content required for provider follow-up requests during streamed tool loops.

#### Scenario: Reasoning is forwarded to continuation request
- **WHEN** a streamed step returns reasoning content and tool calls
- **AND** generation continues after tool execution
- **THEN** the assistant message appended to the next provider request SHALL include the reasoning content and provider metadata captured from the streamed step
- **AND** the generic streaming loop SHALL remain provider-neutral

#### Scenario: Reasoning and answer text remain separate while tools stream
- **WHEN** a tool-enabled stream emits both reasoning and text across one or more steps
- **THEN** reasoning content SHALL be emitted only through reasoning stream parts
- **AND** answer content SHALL be emitted only through text stream parts

### Requirement: Streaming tool lifecycle compatibility
The system SHALL model tool lifecycle events in Halo-owned stream parts while following AI SDK-style ordering semantics.

#### Scenario: No synthetic partial tool input deltas
- **WHEN** the provider adapter exposes only completed tool calls
- **THEN** the stream SHALL emit completed `tool-call` parts
- **AND** the stream SHALL NOT synthesize partial tool input delta parts from incomplete or unavailable data

#### Scenario: Optional partial tool input events
- **WHEN** a provider adapter exposes reliable partial tool-call input deltas
- **THEN** the system MAY emit Halo-owned partial tool input lifecycle parts before the completed `tool-call`
- **AND** the completed `tool-call` SHALL remain the authoritative input used for server-side execution

### Requirement: Structured output during streaming tool calls
The streaming tool-call loop SHALL preserve structured output behavior when tools and structured output are used together.

#### Scenario: Structured final answer after streamed tools
- **WHEN** a `streamText` request includes tools and a structured output specification
- **AND** tool execution continues to a final answer step
- **THEN** the final answer step SHALL be validated as structured output
- **AND** the parsed structured output SHALL NOT be emitted as an additional stream part

#### Scenario: Tool event ordering with structured output
- **WHEN** a structured output stream includes tool calls
- **THEN** `tool-call` and `tool-result` parts SHALL be emitted before the later answer step's text answer
- **AND** callers SHALL be able to associate tool events and the structured JSON text with the same assistant response

### Requirement: Streaming tool calls through StreamTextResult
The streaming tool-call loop SHALL remain progressive when exposed through `StreamTextResult`.

#### Scenario: Full stream preserves tool order
- **WHEN** a `StreamTextResult.fullStream()` includes streamed tool execution
- **THEN** `tool-call` parts SHALL be emitted before their corresponding `tool-result` or `tool-error` parts
- **AND** later answer text from continuation steps SHALL be emitted after the relevant tool result history is appended

#### Scenario: Text stream excludes tool events
- **WHEN** a `StreamTextResult.textStream()` is consumed for a tool-enabled generation
- **THEN** it SHALL emit only answer text deltas from provider steps
- **AND** it SHALL NOT emit serialized tool calls, tool results, or tool errors as answer text

#### Scenario: Tool execution is not duplicated
- **WHEN** both `fullStream()` and `textStream()` are consumed from the same `StreamTextResult`
- **THEN** each server-side tool call SHALL be executed at most once

### Requirement: Tool loops use resolved step control
The system SHALL use the resolved `stopWhen` and `prepareStep` controls when executing streaming and non-streaming tool loops.

#### Scenario: Tool loop continues by stop condition
- **WHEN** a step emits executable tool calls and the resolved stop condition allows another step
- **THEN** the system MUST execute tool callbacks, append tool results, and invoke the next model step

#### Scenario: Tool loop stops by stop condition
- **WHEN** a step emits executable tool calls but the resolved stop condition rejects another step
- **THEN** the system MUST finish generation without executing another model step

#### Scenario: Omitted stop condition stays single-step
- **WHEN** a request does not set `stopWhen`
- **THEN** the system MUST perform at most one model step

### Requirement: Prepared active tools constrain provider tools
The system SHALL honor per-step active tool overrides before converting tools to provider callbacks.

#### Scenario: Active tools selects one tool
- **WHEN** `prepareStep` returns only tool `weather` as active
- **THEN** the provider request for that step MUST include `weather` and MUST NOT include other request tools

#### Scenario: Active tools references missing tool
- **WHEN** `prepareStep` references a tool name not present in the request
- **THEN** the system MUST fail the request with a validation error before invoking the provider for that step

### Requirement: Tool execution metadata is preserved per step
The system SHALL record tool calls, tool results, tool errors, duration metadata, and provider metadata in the step that produced them.

#### Scenario: Tool succeeds
- **WHEN** a server-side tool executor returns successfully
- **THEN** the full stream MUST emit `tool-result` and the corresponding generation step MUST include that result

#### Scenario: Tool fails
- **WHEN** a server-side tool executor fails
- **THEN** the full stream MUST emit `tool-error` and the corresponding generation step MUST include that tool error
