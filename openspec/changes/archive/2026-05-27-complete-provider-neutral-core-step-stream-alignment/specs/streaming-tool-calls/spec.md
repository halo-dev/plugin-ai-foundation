## ADDED Requirements

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

### Requirement: Tool input streaming events are emitted when available
The system SHALL emit `tool-input-start` and `tool-input-delta` full-stream parts when the provider exposes incremental tool input.

#### Scenario: Provider streams tool arguments
- **WHEN** the provider emits incremental arguments for a tool call
- **THEN** the full stream MUST emit `tool-input-start`, one or more `tool-input-delta` parts, and a final `tool-call` part for the same call id

#### Scenario: Provider only emits complete tool call
- **WHEN** the provider only exposes complete tool call arguments
- **THEN** the full stream MUST emit `tool-call` without synthesizing fake tool input deltas

### Requirement: Tool execution metadata is preserved per step
The system SHALL record tool calls, tool results, tool errors, duration metadata, and provider metadata in the step that produced them.

#### Scenario: Tool succeeds
- **WHEN** a server-side tool executor returns successfully
- **THEN** the full stream MUST emit `tool-result` and the corresponding generation step MUST include that result

#### Scenario: Tool fails
- **WHEN** a server-side tool executor fails
- **THEN** the full stream MUST emit `tool-error` and the corresponding generation step MUST include that tool error
