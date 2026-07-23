## Purpose

Define provider-neutral tool input and output schema validation for request-scoped language model tools.
## Requirements
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

### Requirement: Tool output schema validation
The system SHALL allow tool definitions to validate executor results before sending them back to the model.

#### Scenario: Tool output schema present
- **WHEN** a tool definition includes an output schema
- **AND** the executor result matches that schema
- **THEN** the system SHALL record or stream the tool result normally
- **AND** the result sent to the next model step SHALL be the validated executor result

#### Scenario: Tool output schema mismatch
- **WHEN** a tool definition includes an output schema
- **AND** the executor result does not match that schema
- **THEN** the system SHALL record or stream a `tool-error`
- **AND** it SHALL stop the multi-step tool loop for that failed tool call

### Requirement: Tool schema metadata
The system SHALL keep tool schema behavior provider-neutral while ensuring public tool schema metadata is either applied by provider adapters that support it or ignored safely by providers that do not.

#### Scenario: Tool input examples
- **WHEN** a tool definition includes input examples
- **AND** the selected provider adapter supports provider-native tool input examples
- **THEN** the provider request SHALL include the examples in the provider-supported form
- **AND** local tool input validation SHALL still run before executor invocation

#### Scenario: Unsupported tool input examples
- **WHEN** a tool definition includes input examples
- **AND** the selected provider adapter does not support provider-native tool input examples
- **THEN** the request SHALL still be allowed
- **AND** the unsupported examples SHALL NOT alter local validation or executor input

#### Scenario: Public tool schema DTOs
- **WHEN** a caller compiles against the `api` module
- **THEN** tool schema fields SHALL use provider-neutral Java collection types and DTOs
- **AND** callers SHALL NOT need Spring AI or provider-native schema classes

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

### Requirement: Tool executor contract
The public tool executor contract SHALL use the execution context as the authoritative input.

#### Scenario: Context-based execution
- **WHEN** a caller defines a server-side tool
- **THEN** the tool executor SHALL receive `ToolExecutionContext`
- **AND** the parsed input SHALL be available from the context instead of a standalone argument map

#### Scenario: Output schema validation still applies
- **WHEN** a context-based tool executor returns a result
- **THEN** the system SHALL validate the result against `ToolDefinition.outputSchema` when present before sending it back to the model

### Requirement: Tool Schemas Use SDK Schema Helpers
Tool input and output schema APIs SHALL accept SDK-provided schema helper types for normal usage while preserving provider-neutral serialization internally.

#### Scenario: Caller defines tool input schema
- **WHEN** a plugin author defines a tool with object input
- **THEN** the author can declare properties, required fields, descriptions, arrays, enums, and nested objects through SDK helpers

#### Scenario: Tool schema is sent to a provider
- **WHEN** a typed tool schema is used in a generation request
- **THEN** the implementation serializes it to the provider-neutral shape expected by the provider adapter without losing schema details

### Requirement: Tool Definitions Avoid Raw Type Strings
Tool definition APIs SHALL provide typed constants or enums for tool and schema concepts that callers need to reference directly.

#### Scenario: Caller configures a tool
- **WHEN** a plugin author configures a tool declaration
- **THEN** the author does not need to know undocumented string literals for supported tool or schema types

### Requirement: Tool Behavior Is Verified End To End
Tool schema helpers and provider-facing metadata SHALL be covered by tests that prove typed SDK construction reaches provider request construction, tool execution, and result handling.

#### Scenario: Tool call round trip
- **WHEN** a request uses a typed tool schema and the model emits a tool call
- **THEN** the implementation validates arguments, invokes the tool, returns the tool result, and preserves the existing tool stream/result contract

#### Scenario: Strict flag reaches supported provider adapter
- **WHEN** a request defines a tool with `strict = true`
- **AND** the selected provider adapter supports provider-native strict tool schemas
- **THEN** provider request construction SHALL carry the strict flag to the native tool definition
- **AND** local input validation SHALL still run before executor invocation

#### Scenario: Unsupported provider metadata is not fake-applied
- **WHEN** a request defines tool metadata that the selected provider adapter does not support
- **THEN** tests SHALL prove the unsupported metadata is ignored safely rather than represented as applied behavior

### Requirement: Tool calling documentation covers multi-step workflows
Consumer documentation SHALL describe tool definitions, tool choice, server-side executors, multi-step control, and tool result aggregation.

#### Scenario: Tool definition is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show `ToolDefinition` with name, description, input schema, optional output schema, and executor

#### Scenario: Tool choice is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL describe auto, required, none, and named-tool choices

#### Scenario: Multi-step tool calls are documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL explain the default one-step behavior and how `StopCondition` enables continued tool-result loops

#### Scenario: Tool lifecycle is documented
- **WHEN** a plugin author needs observability or auditing
- **THEN** the guide SHALL explain caller-visible lifecycle callbacks, tool results, tool errors, and warnings without exposing internal orchestration classes

### Requirement: Tool Approval Runs After Input Validation
The system SHALL decide tool execution approval only after the model-produced input has been parsed and validated.

#### Scenario: Invalid input does not request approval
- **WHEN** a model returns a tool call whose input does not match the tool input schema
- **THEN** the system SHALL record or emit a `tool-error`
- **AND** it SHALL NOT evaluate approval policy for that invalid tool call

#### Scenario: Approval predicate receives validated input
- **WHEN** a tool approval predicate is evaluated
- **THEN** it SHALL receive the validated parsed input through provider-neutral execution context
- **AND** it SHALL NOT receive Spring AI or provider-native message types

### Requirement: Tool Definition Approval API Is Provider-Neutral
Tool approval configuration SHALL be declared through public SDK types that do not depend on Spring AI or provider-native classes.

#### Scenario: Caller declares approval policy
- **WHEN** a plugin author defines a tool
- **THEN** the author can configure no approval, always approval, or dynamic approval without using raw string literals

#### Scenario: Provider adapter receives tools
- **WHEN** a request with approval-aware tools is converted for a provider
- **THEN** approval policy SHALL remain enforced by the Halo app layer
- **AND** provider adapters SHALL continue receiving provider-supported tool declarations without approval-only runtime callbacks

### Requirement: Tool schema metadata survives Spring AI RC1 migration
The system SHALL preserve provider-neutral tool schema validation and provider-native tool schema metadata behavior after migrating to Spring AI 2.0.0-RC1 tool definition APIs.

#### Scenario: Local tool validation remains authoritative
- **WHEN** a model returns a tool call after the RC1 migration
- **THEN** the system SHALL validate the parsed input against the public `ToolDefinition.inputSchema` before executor invocation
- **AND** the validation result SHALL NOT depend on Spring AI provider-native validation

#### Scenario: Strict schema reaches supported provider
- **WHEN** a request defines a tool with `strict = true`
- **AND** the selected RC1 provider adapter can represent provider-native strict tool schema metadata
- **THEN** the provider request SHALL carry the strict flag in the provider-supported form
- **AND** local input validation SHALL still run before executor invocation

#### Scenario: Strict schema downgrade is visible
- **WHEN** a request defines a tool with `strict = true`
- **AND** the selected RC1 provider adapter cannot represent provider-native strict tool schema metadata
- **THEN** the request SHALL still use local input validation
- **AND** the generation result or stream step SHALL include a stable warning instead of reporting strict native enforcement as applied

#### Scenario: Tool schema DTOs stay provider-neutral
- **WHEN** a consumer plugin constructs tool definitions with public SDK helpers
- **THEN** the caller SHALL NOT need to import Spring AI RC1 tool, metadata, or provider-native schema classes

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
