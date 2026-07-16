## ADDED Requirements

### Requirement: Effective reasoning-history capability
The runtime SHALL resolve reasoning-history support from the model-level tri-state override and the provider default into one effective capability value.

#### Scenario: Explicit model support wins
- **WHEN** a language model sets `capabilities.language.reasoningHistory = true`
- **THEN** the effective language capability SHALL report reasoning-history support
- **AND** reasoning history SHALL be preserved and accepted by runtime history handling

#### Scenario: Explicit model rejection wins
- **WHEN** a language model sets `capabilities.language.reasoningHistory = false`
- **THEN** the effective language capability SHALL report reasoning-history as unsupported
- **AND** runtime validation SHALL reject caller-provided assistant reasoning history before provider invocation

#### Scenario: Unknown model value inherits provider default
- **WHEN** a language model omits `capabilities.language.reasoningHistory` or sets it to null
- **THEN** the effective value SHALL equal the provider's reasoning-history default

#### Scenario: Runtime consumers use the same effective value
- **WHEN** a resolved language model is composed for invocation
- **THEN** capability reporting, request validation, message history assembly, and UI-message chat conversion SHALL use the same effective reasoning-history value
