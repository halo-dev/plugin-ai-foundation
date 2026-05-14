## Why

`ModelConsoleEndpoint` and `ProviderConsoleEndpoint` mix custom business endpoints with simple CRUD wrappers around `ReactiveExtensionClient`. Several endpoints (`getModel`, `deleteModel`, `getProvider`, `updateProvider`) are thin pass-throughs with no added validation or logic. Halo's built-in Extension API already provides identical operations for these. Removing the redundant wrappers reduces maintenance surface while keeping endpoints that have real business value: non-paginated list queries, create/update validations, cascade delete guards, and custom actions like `test-chat` and `discover-models`.

## What Changes

- **Remove** the following pass-through endpoints from `ModelConsoleEndpoint`:
  - `GET /apis/console.api.aifoundation.halo.run/v1alpha1/models/{name}` — simple `client.fetch`, Extension API provides identical behavior
  - `DELETE /apis/console.api.aifoundation.halo.run/v1alpha1/models/{name}` — simple `client.fetch` + `client.delete`, no cascade or validation logic
  - Keep: `GET /models` (non-paginated list), `POST /models` (with `providerName` + `modelId` uniqueness validation), `PUT /models/{name}` (with uniqueness validation), `POST /models/{name}/test-chat`

- **Remove** the following pass-through endpoints from `ProviderConsoleEndpoint`:
  - `GET /apis/console.api.aifoundation.halo.run/v1alpha1/providers/{name}` — simple `client.fetch`, Extension API provides identical behavior
  - `PUT /apis/console.api.aifoundation.halo.run/v1alpha1/providers/{name}` — simple `client.fetch` + `client.update`, no added validation
  - Keep: `GET /providers` (non-paginated list), `POST /providers` (with `providerType` existence validation), `DELETE /providers/{name}` (with associated-model cascade guard), `GET /providers/{name}/discover-models`, `POST /providers/{name}/connectivity`

- **Update frontend** to use Extension API (`aiCoreApiClient`) only for the removed endpoints:
  - Model delete handler → switch to `aiCoreApiClient.model.deleteAiModel`
  - Any direct model get call → switch to Extension API or derive from list data
  - Provider update in any modal still using console API → switch to `aiCoreApiClient.provider.updateAiProvider`
  - List, create, and provider delete calls stay on console API (retained endpoints)

- **Regenerate API client** (`./gradlew generateApiClient`) so generated TypeScript code no longer includes removed endpoints.

## Capabilities

### New Capabilities
<!-- None — this is a refactoring change. -->

### Modified Capabilities
- `console-model-management`: Implementation is being updated to match existing spec requirements. The spec already states that provider/model CRUD must go through the Extension API; this change removes the redundant custom endpoints that were deviations from the spec.

## Impact

- **Backend**: `ModelConsoleEndpoint.java`, `ProviderConsoleEndpoint.java`
- **Frontend**: model delete handlers, any direct model/provider get call, provider update in modals still using console API
- **Generated API client**: Files under `ui/src/api/generated/api/console-api-aifoundation-halo-run-v1alpha1-*`
- **Behavior changes**: Minimal — only the removed endpoints change; retained endpoints (list, create with validation, provider delete with cascade guard) continue to work as before
