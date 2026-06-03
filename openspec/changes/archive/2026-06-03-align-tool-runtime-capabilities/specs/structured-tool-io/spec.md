## MODIFIED Requirements

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
The system SHALL pass a provider-neutral execution context to server-side tools.

#### Scenario: Tool context contains call identity
- **WHEN** the system invokes a tool executor for a model-produced tool call
- **THEN** the executor SHALL receive the tool call id, tool name, parsed input, and zero-based step index

#### Scenario: Tool context contains messages
- **WHEN** the system invokes a tool executor during a multi-step generation
- **THEN** the executor context SHALL include the messages that led to the tool call
- **AND** those messages SHALL include prior assistant tool calls and tool results already appended for the current generation loop

#### Scenario: Tool context contains cancellation
- **WHEN** a request includes a cancellation token
- **AND** the system invokes a server-side tool executor
- **THEN** the executor context SHALL expose the same provider-neutral cancellation token
- **AND** the executor SHALL be able to check cancellation without depending on Spring AI or provider-native classes

#### Scenario: Tool context is provider-neutral
- **WHEN** a consumer compiles against the `api` module
- **THEN** the tool execution context SHALL NOT expose Spring AI or provider-native message types

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
