# ai-provider-config Specification

## Purpose
Define provider configuration semantics for built-in and custom AI provider types.
## Requirements
### Requirement: Provider configuration per type
The system SHALL interpret `AiProvider` structured fields according to the `AiProviderType` metadata, not according to hardcoded per-type rules.

#### Scenario: Built-in provider preset configuration
- **WHEN** a user creates an `AiProvider` with a built-in provider type (where `AiProviderType.isBuiltIn()` is true)
- **THEN** the user SHALL be able to complete configuration by selecting the provider type and binding `apiKeySecretName`
- **AND** the system SHALL use the `AiProviderType.getDefaultBaseUrl()` and provider-specific request behavior for that type
- **AND** `AiProviderType.getDefaultBaseUrl()` SHALL represent the provider-documented API base URL for that provider integration
- **AND** the user SHALL NOT be required to manually enter the provider's API base URL
- **AND** the user MAY provide `spec.baseUrl` to override the built-in default base URL

#### Scenario: Provider type requiring base URL
- **WHEN** a user creates an `AiProvider` with a provider type where `AiProviderType.requiresBaseUrl()` is true
- **THEN** the user SHALL be required to provide `spec.baseUrl`
- **AND** the provided value SHALL represent the provider-documented API base URL for that provider integration

#### Scenario: Provided base URL is used without legacy normalization
- **WHEN** a user provides `spec.baseUrl`
- **THEN** the runtime SHALL use that base URL as the API base URL for the provider integration
- **AND** the runtime SHALL NOT append inferred version or platform prefixes for legacy root-style values

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

