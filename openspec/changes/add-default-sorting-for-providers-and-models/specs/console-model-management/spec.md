## MODIFIED Requirements

### Requirement: Provider list view
The Console UI SHALL display a list of all `AiProvider` Extensions with their type, display name, and status.

#### Scenario: View all providers
- **WHEN** an admin opens the AI model configuration page
- **THEN** the system SHALL fetch all providers via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers`)
- **AND** the response SHALL be a non-paginated array sorted by `metadata.creationTimestamp` descending
- **AND** each item SHALL show `providerType`, `displayName`, `enabled` status, and `status.phase`

### Requirement: Model list view
The Console UI SHALL display provider-scoped `AiModel` entries in the selected provider workspace.

#### Scenario: View all models
- **WHEN** an admin selects a provider
- **THEN** the system SHALL fetch models via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`) with `fieldSelector=spec.providerName={selectedProvider}`
- **AND** the response SHALL be a non-paginated array sorted by `metadata.creationTimestamp` descending
- **AND** each item SHALL show the model display name and underlying `providerResourceName/modelId` reference, where `providerResourceName = AiProvider.metadata.name`
- **AND** each item SHALL show its group and capability tags when available
