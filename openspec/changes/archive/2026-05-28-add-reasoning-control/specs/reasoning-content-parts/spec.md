## ADDED Requirements

### Requirement: Reasoning control preserves output semantics
Request-scoped reasoning controls SHALL affect provider invocation without changing how returned reasoning content is represented.

#### Scenario: Disabled reasoning returns no reasoning
- **WHEN** a caller disables reasoning
- **AND** the provider honors that setting and returns no reasoning content
- **THEN** generation results and stream results SHALL omit reasoning text or expose empty reasoning part lists
- **AND** answer text SHALL remain available normally

#### Scenario: Disabled reasoning still returns reasoning
- **WHEN** a caller disables reasoning
- **AND** the provider still returns reasoning content
- **THEN** the SDK SHALL preserve the returned reasoning parts separately from answer text
- **AND** the result or stream step SHALL include a stable warning that reasoning content was returned despite the disabled request

#### Scenario: Reasoning history with disabled reasoning
- **WHEN** a caller disables reasoning for a request that contains assistant reasoning history
- **THEN** the request SHALL be rejected before invocation unless the provider explicitly supports combining disabled reasoning with reasoning history
- **AND** the error message SHALL identify the conflict between disabled reasoning and reasoning history
