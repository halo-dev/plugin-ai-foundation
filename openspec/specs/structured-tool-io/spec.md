## Purpose

Define provider-neutral tool input and output schema validation for request-scoped language model tools.

## Requirements

### Requirement: Tool input schema validation
The system SHALL validate model-produced tool inputs against request-scoped tool input schemas before executing tools.

#### Scenario: Valid tool input
- **WHEN** a model returns a tool call whose input matches the tool input schema
- **THEN** the tool executor SHALL be invoked with the parsed input
- **AND** normal tool result handling SHALL continue

#### Scenario: Invalid tool input
- **WHEN** a model returns a tool call whose input does not match the tool input schema
- **THEN** the system SHALL NOT invoke the tool executor
- **AND** it SHALL emit or record a `tool-error` with a safe validation message

#### Scenario: Strict tool schema
- **WHEN** a tool definition sets `strict = true`
- **THEN** the provider adapter SHALL request provider-native strict schema enforcement when supported
- **AND** the local tool input validation SHALL still run before executor invocation

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
The system SHALL keep tool schema behavior provider-neutral while allowing providers to use supported schema features.

#### Scenario: Tool input examples
- **WHEN** a tool definition includes input examples
- **THEN** provider adapters MAY pass those examples to providers that support them
- **AND** providers that do not support examples SHALL ignore them without failing the request

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
