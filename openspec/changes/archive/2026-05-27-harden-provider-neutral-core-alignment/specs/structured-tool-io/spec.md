## ADDED Requirements

### Requirement: Tool execution context
The system SHALL pass a provider-neutral execution context to server-side tools.

#### Scenario: Tool context contains call identity
- **WHEN** the system invokes a tool executor for a model-produced tool call
- **THEN** the executor SHALL receive the tool call id, tool name, parsed input, and zero-based step index

#### Scenario: Tool context contains messages
- **WHEN** the system invokes a tool executor during a multi-step generation
- **THEN** the executor context SHALL include the messages that led to the tool call
- **AND** those messages SHALL include prior assistant tool calls and tool results already appended for the current generation loop

#### Scenario: Tool context is provider-neutral
- **WHEN** a consumer compiles against the `api` module
- **THEN** the tool execution context SHALL NOT expose Spring AI or provider-native message types

### Requirement: Tool executor contract
The public tool executor contract SHALL use the execution context as the authoritative input.

#### Scenario: Context-based execution
- **WHEN** a caller defines a server-side tool
- **THEN** the tool executor SHALL receive `ToolExecutionContext`
- **AND** the parsed input SHALL be available from the context instead of a standalone argument map

#### Scenario: Output schema validation still applies
- **WHEN** a context-based tool executor returns a result
- **THEN** the system SHALL validate the result against `ToolDefinition.outputSchema` when present before sending it back to the model
