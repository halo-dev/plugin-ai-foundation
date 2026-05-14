## 1. Backend — Remove pass-through endpoints

- [x] 1.1 Remove `getModel` route handler and OpenAPI doc builder from `ModelConsoleEndpoint.java`
- [x] 1.2 Remove `deleteModel` route handler and OpenAPI doc builder from `ModelConsoleEndpoint.java`
- [x] 1.3 Remove `getProvider` route handler and OpenAPI doc builder from `ProviderConsoleEndpoint.java`
- [x] 1.4 Remove `updateProvider` route handler and OpenAPI doc builder from `ProviderConsoleEndpoint.java`
- [x] 1.5 Verify `ModelConsoleEndpoint` still exposes `GET /models` (non-paginated list), `POST /models` (with validation), `PUT /models/{name}` (with validation), and `POST /models/{name}/test-chat`
- [x] 1.6 Verify `ProviderConsoleEndpoint` still exposes `GET /providers` (non-paginated list), `POST /providers` (with validation), `DELETE /providers/{name}` (with cascade guard), `GET /providers/{name}/discover-models`, and `POST /providers/{name}/connectivity`
- [x] 1.7 Compile backend (`./gradlew compileJava`) to verify no errors

## 2. Frontend — Migrate removed endpoint callers to Extension API

- [x] 2.1 Update `ProviderDetail.vue` to call `aiCoreApiClient.provider.getAiProvider` instead of `aiConsoleApiClient.provider.getProvider`
- [x] 2.2 Update `ProviderModelListItem.vue` to call `aiCoreApiClient.model.deleteAiModel` instead of `aiConsoleApiClient.model.deleteModel`
- [x] 2.3 Verify `ProviderDetail.vue` still calls `aiConsoleApiClient.provider.deleteProvider` for provider deletion (retained endpoint with cascade guard)
- [x] 2.4 Verify `ProviderEditingModal.vue` already uses `aiCoreApiClient.provider.updateAiProvider` (no change needed)
- [x] 2.5 Verify `use-providers-fetch.ts` still calls `aiConsoleApiClient.provider.listProviders` (retained non-paginated list)
- [x] 2.6 Verify `use-models-fetch.ts` still calls `aiConsoleApiClient.model.listModels` (retained non-paginated list)
- [x] 2.7 Verify `ProviderCreationModal.vue` still calls `aiConsoleApiClient.provider.createProvider` (retained with validation)
- [x] 2.8 Verify `ModelCreationModal.vue` still calls `aiConsoleApiClient.model.createModel` (retained with validation)
- [x] 2.9 Verify `ModelEditingModal.vue` still calls `aiConsoleApiClient.model.updateModel` (retained with validation)

## 3. API client regeneration and cleanup

- [x] 3.1 Run `./gradlew generateApiClient` to regenerate TypeScript API client
- [x] 3.2 Verify generated files no longer contain removed endpoints (`getModel`, `deleteModel`, `getProvider`, `updateProvider`)
- [x] 3.3 Remove any unused imports of removed console API types from frontend files
- [x] 3.4 Run `cd ui && pnpm type-check` to verify frontend types are correct

## 4. Verification

- [x] 4.1 Start Halo dev server (`docker rm halo-for-plugin-development -f && ./gradlew haloServer`)
- [x] 4.2 Open Console UI at `http://127.0.0.1:8090/console/`
- [x] 4.3 Verify provider list loads correctly (non-paginated list endpoint)
- [x] 4.4 Verify provider detail loads correctly (migrated to Extension API get)
- [x] 4.5 Verify creating a new provider works (retained create with validation)
- [x] 4.6 Verify editing a provider works (migrated to Extension API update)
- [x] 4.7 Verify deleting a provider works with cascade guard still active (retained delete)
- [x] 4.8 Verify model list loads correctly for a selected provider (non-paginated list endpoint)
- [x] 4.9 Verify creating a new model works (retained create with validation)
- [x] 4.10 Verify editing a model works (retained update with validation)
- [x] 4.11 Verify deleting a model works (migrated to Extension API delete)
- [x] 4.12 Verify `test-chat` endpoint still works
- [x] 4.13 Verify `discover-models` endpoint still works
- [x] 4.14 Verify `test-connectivity` endpoint still works
