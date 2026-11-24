## Why

Model discovery currently creates models but does not describe how remote results relate to saved
`AiModel` resources. Administrators importing a large provider catalog cannot tell new models from
models that already exist, locally disabled models, or models that the provider no longer returns.
Repeated imports of an existing catalog fail one by one, and there is no controlled way to refresh
remote metadata without risking locally maintained configuration.

## What Changes

- Enrich the discovery response with a structured per-model sync state (`new`, `existing`,
  `disabled`), the matching local model name, a `syncable` flag, and the differences between the
  remote profile and the saved model.
- Return `missingModels` for saved models that the provider no longer lists. They are reported,
  never deleted.
- Add a provider-scoped batch sync endpoint that applies `create`, `update`, `enable`, `disable`,
  and `ignore` operations and returns a per-model outcome instead of failing the whole batch.
- Define discovery-owned fields (`modelType`, `adapterType`, `features`, capabilities, catalog
  provenance, discovery source/confidence) and administrator-owned fields (`displayName`,
  `enabled`, `parameterMappings`, `parameterConstraints`, `nativeOptions`). Sync never writes
  administrator-owned fields, except that an explicit enable/disable operation changes `enabled`.
- Preserve capability domains whose source is `manual`, and refuse to apply remote changes to
  models whose `discoverySource` is `manual`.
- Group the Console discovery modal by sync state, show differences, expose batch actions with an
  impact confirmation, and refresh both discovery and the provider model list after syncing.

## Capabilities

### New Capabilities

- `model-discovery-sync`: Discovery-to-saved-model reconciliation, field ownership, batch sync
  operations, and Console presentation.

### Modified Capabilities

- `console-model-management`: The discovery modal now presents sync state and batch sync actions
  instead of only creating missing models.

## Non-goals

- Scheduled or automatic background synchronization.
- Deleting saved models when the provider stops returning them.
- Migrating model configuration between providers.
- Persisting an ignore list across sessions.

## Impact

- **Backend:** new discovery analysis and a new Console endpoint under
  `providers/{name}/sync-models`; additive response fields. Existing endpoints keep their paths and
  semantics.
- **Console UI:** `ProviderModelsDiscoveryModal` uses the new response fields and endpoint.
- **Persisted Extension data:** none. No new `AiModel` fields and no migration.
- **Java/npm SDK:** unchanged. The sync types exist only in the Console API surface.
- **Generated contracts:** regenerate OpenAPI and the TypeScript client; do not edit generated
  files manually.
- **Compatibility:** additive API fields and a new endpoint. Previous clients keep working.
