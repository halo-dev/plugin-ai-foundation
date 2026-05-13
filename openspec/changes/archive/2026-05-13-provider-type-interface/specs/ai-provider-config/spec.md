## MODIFIED Requirements

### Requirement: Supported provider types
The system SHALL validate `spec.providerType` at provider creation time by checking that a corresponding `AiProviderType` bean exists in the Spring context. The system SHALL NOT maintain a hardcoded list of supported provider types.

#### Scenario: Provider type validation
- **WHEN** a user creates an `AiProvider` with `spec.providerType = "openai"`
- **THEN** the system SHALL look up an `AiProviderType` bean where `getProviderType()` returns "openai"
- **AND** if found, the system SHALL accept the resource

#### Scenario: Unsupported provider type rejection
- **WHEN** a user creates an `AiProvider` with `spec.providerType = "unknown"`
- **THEN** the system SHALL reject the resource with a validation error
- **AND** the error message SHALL indicate that no provider type matching "unknown" was found

### Requirement: Provider configuration per type
The system SHALL interpret `AiProvider` structured fields according to the `AiProviderType` metadata, not according to hardcoded per-type rules.

#### Scenario: Built-in provider preset configuration
- **WHEN** a user creates an `AiProvider` with a built-in provider type (where `AiProviderType.isBuiltIn()` is true)
- **THEN** the user SHALL be able to complete configuration by selecting the provider type and binding `apiKeySecretName`
- **AND** the system SHALL use the `AiProviderType.getDefaultBaseUrl()` and provider-specific request behavior for that type
- **AND** the user SHALL NOT be required to manually enter the provider's API base URL

#### Scenario: Provider type requiring base URL
- **WHEN** a user creates an `AiProvider` with a provider type where `AiProviderType.requiresBaseUrl()` is true
- **THEN** the user SHALL be required to provide `spec.baseUrl`

## REMOVED Requirements

### Requirement: AiProvider Extension definition
**Reason**: The core Extension definition is unchanged; only the `spec.config` map field is removed as it was never used.
**Migration**: The `spec.config` field is removed from `AiProviderSpec`. No migration needed as it was never read or written.

### Requirement: Embedding batch limits per provider
**Reason**: Embedding batch limits are now defined on `AiProviderType` (see `provider-type-registry` spec's `maxEmbeddingsPerCall()` and `supportsParallelCalls()` requirements).
**Migration**: Use `AiProviderType.maxEmbeddingsPerCall()` and `AiProviderType.supportsParallelCalls()` instead of hardcoded per-provider values.
