### Requirement: Reconciler detects deleted provider

The system SHALL use a `Reconciler` to monitor `AiProvider` Extensions and detect when they are deleted.

#### Scenario: Provider deletion triggers reconcile
- **WHEN** an `AiProvider` Extension is deleted via any API path (console or core)
- **THEN** the `AiProviderReconciler` SHALL receive a reconcile request for that provider name

### Requirement: Cascade delete associated models

When a provider deletion is detected, the system SHALL delete all `AiModel` Extensions whose `spec.providerName` matches the deleted provider.

#### Scenario: Models are cleaned up after provider deletion
- **WHEN** the reconciler detects that an `AiProvider` no longer exists
- **THEN** it SHALL query all `AiModel` entries with `spec.providerName` equal to the deleted provider's name
- **AND** it SHALL delete each associated `AiModel`
- **AND** the deletion SHALL complete without manual intervention

#### Scenario: No models exist for deleted provider
- **WHEN** the reconciler detects a deleted provider
- **AND** no `AiModel` entries reference that provider
- **THEN** the reconciler SHALL complete successfully with no action taken

#### Scenario: Model deletion failure triggers retry
- **WHEN** the reconciler attempts to delete an associated model
- **AND** the deletion fails (e.g., due to a transient database error)
- **THEN** the reconciler SHALL requeue the request with a retry delay
