## ADDED Requirements

### Requirement: Discovery reports sync state for every remote model
The provider model discovery response SHALL classify each discovered model against saved `AiModel`
resources of the same provider by `spec.modelId`.

#### Scenario: Model has no saved counterpart
- **WHEN** discovery returns a model id that no saved model of the provider uses
- **THEN** the item SHALL have sync status `new`
- **AND** the item SHALL be `syncable`

#### Scenario: Model already exists and is enabled
- **WHEN** a saved model with the same `modelId` exists and `spec.enabled` is true
- **THEN** the item SHALL have sync status `existing`
- **AND** the item SHALL include the saved resource name and display name

#### Scenario: Model already exists and is disabled locally
- **WHEN** a saved model with the same `modelId` exists and `spec.enabled` is false
- **THEN** the item SHALL have sync status `disabled`
- **AND** the item SHALL remain eligible for explicit enable and disable operations

### Requirement: Discovery reports remote-missing saved models
The discovery response SHALL list saved models of the provider that the remote provider no longer
returns.

#### Scenario: Saved model is absent from discovery
- **WHEN** a saved model's `modelId` is missing from the discovery result
- **THEN** the response SHALL include it under `missingModels` with its resource name, display name,
  model id, type, and enabled state
- **AND** discovery SHALL NOT delete or modify it

### Requirement: Discovery exposes field differences
For models with a saved counterpart, discovery SHALL return structured differences between the
remote profile and the saved configuration.

#### Scenario: Differences include administrator-owned fields
- **WHEN** the remote display name differs from the saved display name
- **THEN** the difference SHALL be reported with `applied=false`
- **AND** the administrator-owned fields `displayName`, `enabled`, `parameterMappings`,
  `parameterConstraints`, and `nativeOptions` SHALL never be written by discovery sync

#### Scenario: Differences include discovery-owned fields
- **WHEN** model type, adapter, features, or capability values differ
- **THEN** each difference SHALL report the local value and the remote value
- **AND** `applied` SHALL be true only when the sync may write that field

#### Scenario: Manually configured capability domain
- **WHEN** a saved model declares a capability domain whose source is `manual`
- **THEN** differences in that domain SHALL be reported with `applied=false`

#### Scenario: Manually created model
- **WHEN** a saved model has `spec.discoverySource` equal to `manual`
- **THEN** the item SHALL have `syncable=false`
- **AND** discovery sync SHALL NOT update its discovery-owned fields

### Requirement: Batch model sync endpoint
The Console API SHALL provide a provider-scoped endpoint that applies model sync operations and
returns one result per operation.

#### Scenario: Create operation
- **WHEN** an administrator submits a `create` operation with a complete model
- **THEN** the system SHALL validate the model, enforce provider/model-id uniqueness, generate a
  resource name, and persist it
- **AND** the result SHALL report outcome `created`

#### Scenario: Update operation
- **WHEN** an administrator submits an `update` operation for an existing syncable model
- **THEN** the system SHALL apply the discovery-owned remote profile
- **AND** the system SHALL preserve administrator-owned fields and `manual` capability domains
- **AND** the result SHALL report outcome `updated`

#### Scenario: Update of a manually created model
- **WHEN** an `update` operation targets a model whose `discoverySource` is `manual`
- **THEN** the system SHALL skip the update
- **AND** the result SHALL report outcome `skipped`

#### Scenario: Enable and disable operations
- **WHEN** an administrator submits `enable` or `disable` operations
- **THEN** the system SHALL change only `spec.enabled`
- **AND** the result SHALL report outcome `enabled` or `disabled`

#### Scenario: Ignore operation
- **WHEN** an administrator submits an `ignore` operation
- **THEN** the system SHALL not write anything
- **AND** the result SHALL report outcome `skipped`

#### Scenario: One operation fails
- **WHEN** one operation fails validation or persistence
- **THEN** the response SHALL report outcome `failed` with a message for that operation
- **AND** remaining operations SHALL still be applied

### Requirement: Console discovery modal presents sync workflow
The Console discovery modal SHALL group discovered models by sync state, show local state and
differences, and provide batch actions with impact confirmation.

#### Scenario: Modal opens with saved models
- **WHEN** an administrator opens discovery for a provider with saved models
- **THEN** models SHALL be grouped as new, existing, and locally disabled
- **AND** remote-missing saved models SHALL be shown separately with a warning
- **AND** new models SHALL be selected by default while existing models SHALL NOT be

#### Scenario: Confirming a batch sync
- **WHEN** an administrator triggers a batch create, sync update, enable, or disable action
- **THEN** the Console SHALL confirm the affected model count and state that administrator-maintained
  fields are preserved
- **AND** after completion the Console SHALL report per-model outcomes and refresh discovery and the
  provider model list
