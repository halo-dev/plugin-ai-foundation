## ADDED Requirements

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
- **AND** `maxSteps` allows another provider step
- **THEN** the system SHALL execute the tool server-side
- **AND** emit a `tool-result` part after the executor completes
- **AND** append assistant tool-call history plus tool result history before starting the next streamed provider step

### Requirement: Multi-step stream lifecycle
The system SHALL expose each streamed provider call as a separate generation step.

#### Scenario: Multiple streamed steps
- **WHEN** tool execution causes generation to continue across multiple provider calls
- **THEN** the stream SHALL emit `start-step` and `finish-step` for each provider step using zero-based step indexes
- **AND** the final `finish` part SHALL be emitted only after the last streamed step completes or the loop stops

#### Scenario: Max steps reached with tool call
- **WHEN** a streamed step returns a tool call
- **AND** `maxSteps` does not allow another provider step
- **THEN** the stream SHALL emit the `tool-call` part
- **AND** the `finish-step` part SHALL include a warning that max steps were reached
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
The system SHALL model tool lifecycle events in Halo-owned stream parts while following external provider-neutral ordering semantics.

#### Scenario: No synthetic partial tool input deltas
- **WHEN** the provider adapter exposes only completed tool calls
- **THEN** the stream SHALL emit completed `tool-call` parts
- **AND** the stream SHALL NOT synthesize partial tool input delta parts from incomplete or unavailable data

#### Scenario: Optional partial tool input events
- **WHEN** a provider adapter exposes reliable partial tool-call input deltas
- **THEN** the system MAY emit Halo-owned partial tool input lifecycle parts before the completed `tool-call`
- **AND** the completed `tool-call` SHALL remain the authoritative input used for server-side execution
