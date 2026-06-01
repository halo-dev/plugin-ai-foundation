## ADDED Requirements

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
