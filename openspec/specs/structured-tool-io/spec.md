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
Tool schema helpers SHALL be covered by tests that prove the typed SDK construction reaches tool execution and result handling.

#### Scenario: Tool call round trip
- **WHEN** a request uses a typed tool schema and the model emits a tool call
- **THEN** the implementation validates arguments, invokes the tool, returns the tool result, and preserves the existing tool stream/result contract

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

