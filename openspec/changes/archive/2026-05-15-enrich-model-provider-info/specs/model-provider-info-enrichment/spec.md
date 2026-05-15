## ADDED Requirements

### Requirement: ModelInfo exposes enabled status

The system SHALL include `enabled` in `ModelInfo` so consumer plugins can determine model availability without attempting to use the model.

#### Scenario: Consumer filters disabled models
- **WHEN** a consumer calls `aiModelService.listModels()`
- **THEN** each `ModelInfo` SHALL include `enabled` reflecting `AiModel.spec.enabled`
- **AND** a consumer can filter out models where `enabled == false`

### Requirement: ProviderInfo exposes last connectivity check time

The system SHALL include `lastCheckedAt` in `ProviderInfo` so the console UI can display when a provider was last verified.

#### Scenario: UI displays last check time
- **WHEN** a consumer calls `aiModelService.listProviders()`
- **THEN** each `ProviderInfo` SHALL include `lastCheckedAt` reflecting `AiProvider.status.lastCheckedAt`
- **AND** `lastCheckedAt` SHALL be `null` when connectivity has never been checked
