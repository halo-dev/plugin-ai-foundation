## ADDED Requirements

### Requirement: Type-Safe Message And Content Parts
The AI model service SDK SHALL expose type-safe construction APIs for user, system, assistant, and tool messages and their supported content parts.

#### Scenario: Caller creates a text message
- **WHEN** a plugin author creates a user message with text content
- **THEN** the SDK provides a direct typed factory or builder without requiring a raw part type string

#### Scenario: Caller attempts an invalid part shape
- **WHEN** a plugin author constructs a content part with fields that do not belong to that part kind
- **THEN** the SDK prevents the invalid shape at construction time or fails validation before provider invocation

### Requirement: Text Generation Fields Have Closed-Loop Behavior
Every supported text generation request setting SHALL have implemented behavior, validation, JavaDoc, and tests.

#### Scenario: Supported setting is passed
- **WHEN** a plugin author sets a supported text generation option
- **THEN** the implementation maps it to provider-neutral or provider-specific behavior and tests cover the mapping

#### Scenario: Unsupported setting is discovered during audit
- **WHEN** a text generation option is found to be unsupported or misleading
- **THEN** it is removed from the public SDK instead of kept for compatibility

### Requirement: Language Model Implementation Has Focused Collaborators
The language generation implementation SHALL be split into cohesive collaborators for validation, message conversion, provider options, tool orchestration, structured output, stream normalization, lifecycle events, and result assembly.

#### Scenario: Tool orchestration is changed
- **WHEN** a developer updates tool-call behavior
- **THEN** the relevant code can be changed and tested without editing unrelated message conversion or lifecycle event code

#### Scenario: Stream normalization is changed
- **WHEN** a developer updates stream part ordering or normalization
- **THEN** tests can target that behavior without depending on the full provider invocation path
