## MODIFIED Requirements

### Requirement: Model info listing

The system SHALL expose `Mono<List<ModelInfo>> listModels()` to list all configured `AiModel` entries.

#### Scenario: List all configured models with enabled status
- **WHEN** a consumer calls `aiModelService.listModels()`
- **THEN** the system SHALL return all `AiModel` Extensions with their `name` (the `metadata.name`), `providerName`, `modelId`, `displayName`, and `enabled`

### Requirement: Provider info listing

The system SHALL expose `Mono<List<ProviderInfo>> listProviders()` to list all configured providers and their status.

#### Scenario: List all providers with last check time
- **WHEN** a consumer calls `aiModelService.listProviders()`
- **THEN** the system SHALL return all `AiProvider` Extensions with their `name`, `displayName`, `providerType`, `enabled`, `phase`, and `lastCheckedAt`
