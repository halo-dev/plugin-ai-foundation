## MODIFIED Requirements

### Requirement: Tool input schema validation
The system SHALL normalize and validate model-produced inputs for every known request-scoped tool before input availability, approval, external handoff, or server-side execution.

#### Scenario: Valid executable tool input
- **WHEN** a model returns a known tool call whose input matches the tool input schema
- **AND** the tool has a server-side executor
- **THEN** the system SHALL publish authoritative input availability before invoking the executor
- **AND** normal tool result handling SHALL continue

#### Scenario: Valid external tool input
- **WHEN** a model returns a known tool call whose input matches the tool input schema
- **AND** the tool has no server-side executor
- **THEN** the system SHALL publish authoritative input availability
- **AND** the validated call SHALL remain pending external work

#### Scenario: Invalid tool input
- **WHEN** a known tool call's input does not match its input schema and cannot be repaired
- **THEN** the system SHALL NOT request approval, expose input availability, or invoke the tool executor
- **AND** it SHALL emit or record a `tool-input-error` with a safe validation message

#### Scenario: Strict tool schema
- **WHEN** a tool definition sets `strict = true`
- **THEN** the provider adapter SHALL request provider-native strict schema enforcement when supported
- **AND** local tool input validation SHALL still run before input availability

### Requirement: Tool execution context
The system SHALL pass immutable provider-neutral context snapshots to tool input callbacks, approval predicates, and server-side executors.

#### Scenario: Tool context contains call identity
- **WHEN** the system invokes a tool callback, approval predicate, or executor
- **THEN** the context SHALL include the tool call id, tool name, zero-based step index, and the fields appropriate to that lifecycle stage

#### Scenario: Tool context contains messages
- **WHEN** the system creates tool context during a multi-step generation
- **THEN** the context SHALL include an immutable snapshot of the messages that led to the tool call
- **AND** those messages SHALL include prior assistant tool calls and tool results already appended for the current generation loop

#### Scenario: Tool context contains request context
- **WHEN** the generation request includes provider-neutral caller context
- **THEN** tool input callbacks, approval predicates, and executors SHALL receive that request context
- **AND** generation lifecycle metadata SHALL remain separate from the tool context contract

#### Scenario: Tool context contains cancellation
- **WHEN** a request includes a cancellation token
- **THEN** every tool input callback, approval predicate, and executor context SHALL expose the same provider-neutral cancellation token
- **AND** consumers SHALL be able to check cancellation without depending on Spring AI or provider-native classes

#### Scenario: Tool context is immutable and provider-neutral
- **WHEN** a consumer compiles against the `api` module
- **THEN** tool contexts SHALL expose immutable provider-neutral values and collections
- **AND** they SHALL NOT expose Spring AI or provider-native message types

## ADDED Requirements

### Requirement: Tool definitions expose input lifecycle callbacks
The public `ToolDefinition` SHALL allow callers to register provider-neutral reactive callbacks for tool input start, delta, and final availability.

#### Scenario: Input start callback
- **WHEN** the runtime is ready to publish `tool-input-start` for a known tool call
- **THEN** it MUST invoke `onInputStart` first and await its `Mono<Void>`
- **AND** the callback context SHALL contain stable identity, step, messages, request context, provider metadata, and cancellation

#### Scenario: Input delta callback
- **WHEN** the runtime is ready to publish a `tool-input-delta`
- **THEN** it MUST invoke `onInputDelta` with the appendable `inputTextDelta` first and await its `Mono<Void>`
- **AND** callbacks and matching public deltas SHALL retain provider event order

#### Scenario: Input available callback
- **WHEN** normalized final input has been published as authoritative input availability
- **THEN** the runtime MUST invoke `onInputAvailable` with that final input and await its `Mono<Void>`
- **AND** approval, external handoff, and server execution SHALL wait for callback completion

#### Scenario: No input end callback
- **WHEN** an incremental tool-input block ends
- **THEN** the public tool definition SHALL NOT require or invoke an `onInputEnd` callback

### Requirement: Tool input callbacks participate in generation control
Tool input callbacks SHALL be backpressured generation work rather than failure-tolerant lifecycle observers.

#### Scenario: Callback fails
- **WHEN** an input start, delta, or available callback fails
- **THEN** the generation SHALL terminate with a safe typed error
- **AND** the tool SHALL NOT proceed to approval, external handoff, or execution

#### Scenario: Callback is slow
- **WHEN** an input callback has not completed
- **THEN** the corresponding lifecycle publication or downstream tool action SHALL wait
- **AND** total and step timeout controls SHALL continue to apply

#### Scenario: Callback observes cancellation
- **WHEN** generation is cancelled before or during an input callback
- **THEN** the callback chain SHALL be cancelled where Reactor can observe cancellation
- **AND** the runtime SHALL check the cancellation token before and after callback invocation

#### Scenario: Tool timeout remains executor-only
- **WHEN** tool input callbacks run before a server executor
- **THEN** the configured tool execution timeout SHALL NOT govern those callbacks
- **AND** it SHALL begin only around the server executor
