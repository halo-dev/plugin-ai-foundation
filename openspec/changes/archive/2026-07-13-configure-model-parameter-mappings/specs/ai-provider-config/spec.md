## ADDED Requirements

### Requirement: Provider parameter mappings are validated configuration
`AiProvider` SHALL support optional administrator parameter mapping overrides that are interpreted using its resolved `AiProviderType` metadata.

#### Scenario: Provider inherits built-in defaults
- **WHEN** an `AiProvider` omits a parameter mapping or selects `INHERIT`
- **THEN** the runtime SHALL use the Provider type built-in mapping default

#### Scenario: Provider selects a compatible template
- **WHEN** an administrator selects a template advertised for that Provider type and parameter
- **THEN** the backend SHALL persist the selection
- **AND** subsequent Model invocations that inherit it SHALL use that template

#### Scenario: Provider marks a parameter unsupported
- **WHEN** an administrator selects `UNSUPPORTED` for a Provider parameter
- **THEN** inheriting Models SHALL omit caller values for that parameter and report warnings

