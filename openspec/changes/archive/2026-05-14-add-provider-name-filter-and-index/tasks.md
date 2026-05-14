## 1. Backend - Index Registration

- [x] 1.1 Register `spec.providerName` index in `AiFoundationPlugin.start()`

## 2. Backend - Endpoint Changes

- [x] 2.1 Add `fieldSelector` and `labelSelector` query parameters to `ModelConsoleEndpoint.listModels()`
- [x] 2.2 Use `SelectorUtil.labelAndFieldSelectorToListOptions()` to build `ListOptions`
- [x] 2.3 Replace predicate scan with indexed query in `ProviderConsoleEndpoint.deleteProvider()`

## 3. Backend - Tests

- [x] 3.1 Update `ModelConsoleEndpointTest` to cover fieldSelector filtering
- [x] 3.2 Update `ProviderConsoleEndpointTest` if deleteProvider test exists

## 4. Frontend

- [x] 4.1 Update `useModelsByProvider` to pass `fieldSelector` query parameter
- [x] 4.2 Remove client-side `.filter()` call

## 5. API Client Regeneration

- [x] 5.1 Run `./gradlew generateApiClient` to regenerate TypeScript client
- [x] 5.2 Verify generated client includes `fieldSelector`/`labelSelector` parameters in `listModels`

## 6. Verification

- [x] 6.1 Run `./gradlew test` to ensure all tests pass
- [ ] 6.2 Start dev server and verify filtering works via browser
