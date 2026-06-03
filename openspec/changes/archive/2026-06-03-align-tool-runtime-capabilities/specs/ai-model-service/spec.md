## MODIFIED Requirements

### Requirement: Lifecycle controls for language generation
The system SHALL allow text-generation callers to configure lifecycle callbacks, timeout, cancellation, metadata, and context through provider-neutral request fields.

#### Scenario: Generation lifecycle callbacks
- **WHEN** a Java caller builds `GenerateTextRequest` with a lifecycle observer
- **THEN** the language model service MUST emit provider-neutral start, step, tool, finish, and error events for the generation path

#### Scenario: Tool executor receives cancellation control
- **WHEN** a Java caller builds `GenerateTextRequest` with a cancellation token
- **AND** a server-side tool executor is invoked
- **THEN** the tool execution context MUST expose that cancellation token
- **AND** the language model service MUST continue enforcing existing cancellation checks before and after tool execution

#### Scenario: Lifecycle controls do not enter provider prompt
- **WHEN** lifecycle metadata, context, timeout, or cancellation controls are attached to a generation request
- **THEN** the system MUST expose the relevant data to lifecycle events or tool execution context
- **AND** it MUST NOT convert lifecycle metadata, context, timeout, or cancellation controls into model prompt messages unless a future explicit feature requests it

#### Scenario: Public request types remain provider-neutral
- **WHEN** a consumer compiles against the `api` module
- **THEN** lifecycle, timeout, cancellation, metadata, and context types MUST remain independent from Spring AI and provider SDK classes
