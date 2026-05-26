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
