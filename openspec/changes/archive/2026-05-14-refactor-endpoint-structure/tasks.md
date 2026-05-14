## 1. Backend — ProviderTypeConsoleEndpoint

- [x] 1.1 Create `ProviderTypeConsoleEndpoint` class with `GET provider-types` endpoint
- [x] 1.2 Copy `listProviderTypes` logic from `ProviderConsoleEndpoint`

## 2. Backend — ProviderConsoleEndpoint

- [x] 2.1 Remove `GET provider-types` route from `ProviderConsoleEndpoint`
- [x] 2.2 Add `GET providers/{name}/discover-models` route (move from ProviderDebugEndpoint, rename, remove local fallback)
- [x] 2.3 Add `POST providers/{name}/connectivity` route (move from ProviderDebugEndpoint)

## 3. Backend — ModelConsoleEndpoint

- [x] 3.1 Add `POST models/{name}/test-chat` route (move from ProviderDebugEndpoint)
- [x] 3.2 Inject `AiModelService` dependency into `ModelConsoleEndpoint`

## 4. Backend — ProviderDebugEndpoint

- [x] 4.1 Delete `ProviderDebugEndpoint.java`

## 5. Backend — Verification

- [x] 5.1 Run `./gradlew compileJava` to ensure backend compiles
- [x] 5.2 Run `./gradlew test` to ensure tests pass

## 6. Frontend — Regenerate API Client

- [x] 6.1 Run `./gradlew generateApiClient` to regenerate TypeScript API client
- [x] 6.2 Verify new API files are generated (`ProviderTypeApi`, updated `ProviderApi` and `ModelApi`, removed `ProviderDebugApi`)

## 7. Frontend — Update Composables and Views

- [x] 7.1 Update `useProviderTypes.ts` to import from new generated `ProviderTypeApi`
- [x] 7.2 Update `ProviderDetail.vue` to import `discoverModels` and `testConnectivity` from updated `ProviderApi`
- [x] 7.3 Update `TestChatModal.vue` to import `testChat` from updated `ModelApi`

## 8. Verification

- [x] 8.1 Run `cd ui && pnpm type-check` to ensure no TypeScript errors
- [x] 8.2 Start dev server and verify all endpoints respond correctly
- [x] 8.3 Verify `provider-types` returns correct data
- [x] 8.4 Verify `discover-models` does not fall back to local models
