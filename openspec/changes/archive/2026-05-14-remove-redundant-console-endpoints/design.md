## Context

`ModelConsoleEndpoint` and `ProviderConsoleEndpoint` mix custom business endpoints with simple CRUD wrappers. After reviewing each endpoint, only the pass-through ones (no added validation or logic) should be removed:

**ModelConsoleEndpoint:**
- `listModels` — uses `client.listAll` (non-paginated). **Keep**: Extension API list is paginated by default.
- `getModel` — simple `client.fetch`. **Remove**: Extension API provides identical fetch.
- `createModel` — `client.create` + `validateModel` (providerName + modelId uniqueness). **Keep**: custom validation.
- `updateModel` — `client.update` + `validateModel`. **Keep**: custom validation.
- `deleteModel` — simple `client.fetch` + `client.delete`. **Remove**: no cascade or validation logic.
- `testChat` — custom `AiModelService` call. **Keep**.

**ProviderConsoleEndpoint:**
- `listProviders` — uses `client.listAll` (non-paginated). **Keep**: Extension API list is paginated by default.
- `getProvider` — simple `client.fetch`. **Remove**: Extension API provides identical fetch.
- `createProvider` — `client.create` + providerType existence check. **Keep**: custom validation.
- `updateProvider` — simple `client.fetch` + `client.update`. **Remove**: no added validation.
- `deleteProvider` — `client.delete` + associated-model cascade guard. **Keep**: custom business rule.
- `discoverModels` — custom provider-type call. **Keep**.
- `testConnectivity` — custom connectivity check + status update. **Keep**.

Frontend already uses a mix: `ProviderEditingModal.vue` uses `aiCoreApiClient` (Extension API), while most other modals and composables use `aiConsoleApiClient`. Only callers of the removed endpoints need to migrate.

## Goals / Non-Goals

**Goals:**
- Remove pass-through endpoints (`getModel`, `deleteModel`, `getProvider`, `updateProvider`) from both ConsoleEndpoints.
- Migrate only the removed endpoint callers in frontend to `aiCoreApiClient` (Extension API).
- Keep endpoints with real business value: non-paginated lists, create/update with validation, provider delete with cascade guard, and custom actions (`test-chat`, `discover-models`, `test-connectivity`).
- Regenerate API client so removed endpoints disappear from generated TypeScript.

**Non-Goals:**
- Moving the retained endpoints' validation logic elsewhere (model uniqueness, provider type check, cascade delete guard). These remain in their respective ConsoleEndpoints.
- Changing the `ProviderTypeConsoleEndpoint` — it is not a wrapper.
- Changing any Extension API behavior or data models.
- Converting list queries to paginated queries.

## Decisions

### Decision: Remove only pass-through endpoints, not all CRUD
**Rationale**: Endpoints that add real value — non-paginated lists, custom validations (model uniqueness, provider type check), and cascade delete guards — should remain. Only the pure wrappers (`getModel`, `deleteModel`, `getProvider`, `updateProvider`) are removed because Halo's Extension API provides identical behavior for these.

### Decision: Retain validation in ConsoleEndpoints
**Rationale**: `createModel`, `updateModel`, `createProvider`, and `deleteProvider` each contain business logic (uniqueness checks, type validation, cascade guards) that the Extension API does not provide. Moving this to Extension validators/webhooks would expand scope. Keeping them in ConsoleEndpoints is the pragmatic choice.

### Decision: Regenerate API client after backend changes
**Rationale**: The generated TypeScript client under `ui/src/api/generated/` is produced by `./gradlew generateApiClient` from the OpenAPI spec. After removing endpoints, regeneration will automatically drop the unused methods. Frontend must then update call sites for the removed endpoints only.

### Decision: Partial frontend migration
**Rationale**: Only callers of removed endpoints migrate to `aiCoreApiClient`. Callers of retained endpoints (list, create with validation, provider delete with cascade guard) stay on `aiConsoleApiClient` because those endpoints still exist and provide value.

## Risks / Trade-offs

- **[Risk] External consumers of removed endpoints break.** → **Mitigation**: This plugin is unreleased; no external consumers are known. If any exist, they should migrate to Extension API paths.
- **[Risk] Frontend still mixes two API clients.** → **Mitigation**: This is intentional and documented. The retained console endpoints have custom behavior that Extension API cannot replace. The mix is now justified, not accidental.

## Migration Plan

1. Remove `getModel` and `deleteModel` route handlers from `ModelConsoleEndpoint`.
2. Remove `getProvider` and `updateProvider` route handlers from `ProviderConsoleEndpoint`.
3. Restart dev server and verify retained endpoints (list, create, update with validation, provider delete with cascade guard) and custom endpoints (`test-chat`, `discover-models`, `connectivity`) still work.
4. Run `./gradlew generateApiClient`.
5. Update frontend call sites of removed endpoints to use `aiCoreApiClient`.
6. Verify provider/model operations in Console UI.

## Open Questions

- None — scope is well-defined.
