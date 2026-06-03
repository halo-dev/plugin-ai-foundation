## ADDED Requirements

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
