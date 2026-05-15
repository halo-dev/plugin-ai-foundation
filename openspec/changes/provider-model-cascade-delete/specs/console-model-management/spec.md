## MODIFIED Requirements

### Requirement: Delete provider

The Console UI SHALL allow admins to delete an `AiProvider` Extension.

#### Scenario: Delete provider
- **WHEN** an admin clicks delete on a provider
- **AND** confirms the deletion in a warning dialog
- **THEN** the system SHALL call DELETE on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers/{name}`)
- **AND** the backend SHALL allow deletion even if the provider has associated `AiModel` entries
- **AND** the associated models SHALL be automatically deleted by the cascade delete reconciler
- **AND** the provider SHALL disappear from the list

## REMOVED Requirements

### Requirement: Block deleting provider with models

**Reason**: Cascade delete via `AiProviderReconciler` now automatically cleans up associated models. Blocking deletion at the console API layer is redundant and creates a poor user experience.

**Migration**: Deleting a provider via console API will now succeed immediately; associated models will be cleaned up asynchronously by the reconciler.
