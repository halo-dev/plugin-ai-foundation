## MODIFIED Requirements

### Requirement: AiModel Extension definition
The system SHALL define an Extension named `AiModel` with GVK `aifoundation.halo.run/v1alpha1`.

#### Scenario: AiModel structure validation
- **WHEN** a user creates an `AiModel` resource
- **THEN** the resource MUST have `spec.providerName`, `spec.modelId`, `spec.displayName`, and `spec.enabled`
- **AND** the resource MUST NOT include a first-class `spec.group` field for model organization
- **AND** the resource MUST support model profile fields including `spec.modelType` and `spec.features`
- **AND** `spec.providerName` MUST reference an existing `AiProvider.metadata.name`
- **AND** `spec.providerName` SHALL denote the provider instance resource name rather than `spec.providerType`

#### Scenario: Model identifier uniqueness
- **WHEN** a user creates or updates an `AiModel`
- **THEN** the tuple `(spec.providerName, spec.modelId)` MUST be unique across all `AiModel` resources
