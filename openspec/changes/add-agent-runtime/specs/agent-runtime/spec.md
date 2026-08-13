## ADDED Requirements

### Requirement: Consumers can define immutable reusable agents
The system SHALL provide a provider-neutral public agent definition that captures stable model orchestration policy and can be reused safely across requests.

#### Scenario: Agent is built with complete defaults
- **WHEN** a consumer builds an agent with an id, model, instructions, tools, output, stop policy, generation settings, middleware, lifecycle, and recovery callback
- **THEN** the agent SHALL retain a defensively copied immutable definition
- **AND** later changes to caller-owned collections or builders MUST NOT alter the agent

#### Scenario: Agent omits optional policy
- **WHEN** a consumer builds an agent with only a model and instructions
- **THEN** the system SHALL supply documented defaults for tools, output, middleware, lifecycle, recovery, and step control

### Requirement: Agent calls use typed isolated input
The system SHALL provide an immutable typed agent call that separates user input and operational controls from agent-owned policy.

#### Scenario: Prompt call is accepted
- **WHEN** a call contains a prompt and typed call options
- **THEN** the runtime SHALL create a fresh effective model request for that invocation

#### Scenario: Message call is accepted
- **WHEN** a call contains provider-neutral model messages and typed call options
- **THEN** the runtime SHALL preserve the messages without mutating the caller list

#### Scenario: Conflicting call input is rejected
- **WHEN** a call contains both a prompt and messages or contains neither
- **THEN** the runtime MUST reject the call before preparation or provider execution

#### Scenario: Call cannot replace agent policy directly
- **WHEN** a caller constructs an agent call
- **THEN** the call API SHALL NOT expose direct replacement fields for instructions, tools, output, stop conditions, or provider-native options

### Requirement: Typed call options are validated before execution
The system SHALL allow an agent definition to declare runtime validation for its typed call options.

#### Scenario: Valid options continue to preparation
- **WHEN** the call-option validator accepts the supplied options
- **THEN** asynchronous call preparation SHALL receive the validated value

#### Scenario: Invalid options stop the call
- **WHEN** the call-option validator rejects the supplied options
- **THEN** the call SHALL fail with a stable validation error
- **AND** preparation, lifecycle start, and provider execution MUST NOT run

#### Scenario: Agent does not require custom options
- **WHEN** an agent uses the no-options form and the caller supplies ordinary prompt or messages
- **THEN** the runtime SHALL execute without requiring a placeholder map or provider-specific DTO

### Requirement: Agents support asynchronous call preparation
The system SHALL allow one asynchronous preparation callback to derive the effective model and model request for each call.

#### Scenario: Preparation changes the current call
- **WHEN** preparation replaces the model or changes instructions, tools, output, semantic generation settings, middleware, or step policy
- **THEN** those changes SHALL apply only to the current call

#### Scenario: Preparation receives complete context
- **WHEN** preparation begins
- **THEN** it SHALL receive the immutable call, validated typed options, base model, and a fresh effective request builder
- **AND** it SHALL be able to read metadata and request context without adding them to the prompt automatically

#### Scenario: Preparation runs once
- **WHEN** an agent executes multiple model steps
- **THEN** call preparation MUST run exactly once before the first generation lifecycle event
- **AND** step preparation SHALL remain responsible for per-step changes

#### Scenario: Preparation fails
- **WHEN** call preparation throws, returns an error, or produces an invalid effective request
- **THEN** the agent call SHALL fail before provider execution
- **AND** no partially prepared state SHALL be retained for another call

### Requirement: Agent request composition has deterministic precedence
The system SHALL compose definition defaults, call input, operational controls, call preparation, and step preparation in a documented deterministic order.

#### Scenario: Definition initializes the request
- **WHEN** a call starts
- **THEN** the runtime SHALL copy agent instructions, tools, output, stop policy, semantic generation defaults, middleware, lifecycle, and recovery into a fresh request

#### Scenario: Operational controls compose safely
- **WHEN** the call supplies metadata, context, headers, cancellation, timeouts, lifecycle observers, or middleware
- **THEN** the runtime SHALL apply them without mutating definition-owned values
- **AND** definition-level middleware and lifecycle behavior SHALL run before call-scoped entries unless the public contract explicitly states otherwise

#### Scenario: Call preparation is final before execution
- **WHEN** call preparation changes a definition-derived setting
- **THEN** the prepared value SHALL be the input to normal language-model request validation

#### Scenario: Step preparation remains step-scoped
- **WHEN** the effective request declares `prepareStep`
- **THEN** its overrides SHALL apply only according to the existing step-control contract

### Requirement: Agents execute bounded multi-step runs by default
The system SHALL provide useful tool-loop behavior while preventing unbounded execution.

#### Scenario: Default stop policy is used
- **WHEN** an agent definition omits a stop condition
- **THEN** the runtime SHALL allow at most 20 model steps
- **AND** it SHALL finish earlier when the underlying execution has no executable continuation

#### Scenario: Custom stop policy is used
- **WHEN** an agent definition or call preparation supplies a stop condition
- **THEN** the runtime SHALL use that condition instead of the default agent step limit

#### Scenario: Direct model defaults remain unchanged
- **WHEN** a consumer invokes `LanguageModel` directly without `stopWhen`
- **THEN** the existing direct-call single-step behavior SHALL remain unchanged

### Requirement: Agent generate and stream share one execution semantics
The system SHALL delegate agent execution to the selected `LanguageModel` and SHALL return the existing normalized result types.

#### Scenario: Non-streaming generation completes
- **WHEN** a consumer invokes agent generation
- **THEN** the runtime SHALL return `GenerateTextResult` with text, structured output, reasoning, sources, files, steps, tools, warnings, usage, response messages, and metadata produced by the existing model runtime

#### Scenario: Streaming generation completes
- **WHEN** a consumer invokes agent streaming
- **THEN** the runtime SHALL return `StreamTextResult` with the existing full stream, text stream, structured output projections, final result, and UI Message conversion helpers

#### Scenario: Equivalent calls have equivalent terminal state
- **WHEN** deterministic provider fixtures execute the same prepared call through generate and stream
- **THEN** their final steps, tool outcomes, finish reason, usage, warnings, response messages, and structured output SHALL be semantically equivalent

#### Scenario: Agent does not implement a second tool loop
- **WHEN** an agent executes tools or multiple model steps
- **THEN** the existing language-model runtime SHALL remain the authoritative execution engine

### Requirement: Agent calls preserve lifecycle and operational controls
The system SHALL preserve existing lifecycle, cancellation, timeout, retry, middleware, and warning behavior across agent execution.

#### Scenario: Lifecycle observes the full agent call
- **WHEN** an agent executes multiple steps and tools
- **THEN** definition-level and call-scoped lifecycle observers SHALL receive the existing start, step, tool, approval, finish, and error events exactly once according to their contract

#### Scenario: Call is cancelled
- **WHEN** the call cancellation token is cancelled during preparation, provider execution, tool execution, or streaming
- **THEN** the runtime SHALL stop safely using the existing typed cancellation behavior

#### Scenario: Timeout expires
- **WHEN** a configured total, step, or tool timeout expires
- **THEN** the runtime SHALL apply the existing timeout scope and typed exception behavior

#### Scenario: Middleware is composed
- **WHEN** definition and call middleware are present
- **THEN** they SHALL wrap the prepared request in deterministic list order without being applied twice

### Requirement: Agent definitions are safe for concurrent reuse
The system SHALL isolate all mutable call, preparation, stream, and result state per invocation.

#### Scenario: Concurrent calls use different options
- **WHEN** two calls execute concurrently on the same agent with different options, context, models selected by preparation, or cancellation tokens
- **THEN** each call SHALL observe only its own effective request and terminal result

#### Scenario: One call fails
- **WHEN** preparation, validation, provider execution, or a tool fails for one concurrent call
- **THEN** the other call SHALL continue without shared error or cancellation state

#### Scenario: Multiple stream views are consumed
- **WHEN** a caller consumes more than one projection from an agent `StreamTextResult`
- **THEN** the provider, preparation callback, tool callbacks, and lifecycle callbacks SHALL each run at most once for the corresponding call

### Requirement: Agent public contracts remain provider-neutral
The public agent API SHALL use AI Foundation messages, tools, schemas, lifecycle types, model interfaces, and provider-neutral metadata only.

#### Scenario: Consumer compiles against the API module
- **WHEN** another Halo plugin compiles an agent without the implementation module
- **THEN** no Spring AI class SHALL appear in the public signature or required construction path

#### Scenario: Provider-native option maps remain unavailable
- **WHEN** a consumer inspects agent definition, call, preparation, and prepared-call types
- **THEN** no caller-writable provider-native option map SHALL be present

### Requirement: Agent UI Message execution reuses the canonical stream
The system SHALL allow agent streams to serve browser chat clients through the existing UI Message contract.

#### Scenario: Agent stream is converted directly
- **WHEN** a consumer calls the existing UI Message conversion on an agent `StreamTextResult`
- **THEN** text, reasoning, source, file, step, tool, approval, finish, abort, and error parts SHALL use the current chunk schema

#### Scenario: No agent-specific wire type is introduced
- **WHEN** a browser client consumes an agent response
- **THEN** it SHALL use the existing stream version, reducer, persistence shape, and Vue chat actions without an agent-specific transport

### Requirement: Agent failures remain explicit and inspectable
The system SHALL distinguish call validation, call preparation, model resolution, generation, tool recovery, cancellation, and timeout failures without hiding them behind a generic agent error.

#### Scenario: Pre-provider failure occurs
- **WHEN** validation or call preparation fails before provider execution
- **THEN** the returned error SHALL identify that phase and SHALL NOT report a generation step that did not occur

#### Scenario: Generation emits warnings
- **WHEN** preparation, recovery, provider adaptation, or structured output produces a non-fatal warning
- **THEN** the warning SHALL be retained in the existing result and stream warning surfaces

#### Scenario: Stream fails after starting
- **WHEN** an agent stream fails after emitting chunks
- **THEN** it SHALL use the existing terminal error and aggregation behavior
