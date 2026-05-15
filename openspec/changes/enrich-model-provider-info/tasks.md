## 1. Backend DTO Updates

- [x] 1.1 Add `enabled` field to `api/src/main/java/run/halo/aifoundation/ModelInfo.java`
- [x] 1.2 Add `lastCheckedAt` field to `api/src/main/java/run/halo/aifoundation/ProviderInfo.java`

## 2. Backend Service Implementation

- [x] 2.1 Update `AiModelServiceImpl.listModels()` to populate `enabled` from `AiModel.spec.enabled`
- [x] 2.2 Update `AiModelServiceImpl.listProviders()` to populate `lastCheckedAt` from `AiProvider.status.lastCheckedAt`
- [x] 2.3 Run `./gradlew compileJava` to verify backend compiles

## 3. API Client Regeneration

- [x] 3.1 Run `./gradlew generateApiClient` to regenerate TypeScript types
- [x] 3.2 Verify `ModelInfo` and `ProviderInfo` in `ui/src/api/generated/` include new fields
  - Note: `ModelInfo` and `ProviderInfo` are Java SDK DTOs, not REST API response types, so they are not generated into the TypeScript client. Frontend uses `AiModel` and `AiProvider` directly, which already expose these fields.

## 4. Frontend Updates

- [x] 4.1 Update `ProviderModelListItem.vue` to display `model.spec.enabled` (already showing from prior #26 fix)
- [x] 4.2 Update `ProviderListItem.vue` to display `provider.spec.enabled` (already showing from prior #24 fix)
- [x] 4.3 Update `ProviderDetail.vue` to show last connectivity check time (`provider.status.lastCheckedAt`)
- [x] 4.4 Run `cd ui && pnpm type-check` to verify frontend types

## 5. Verification

- [x] 5.1 Run `./gradlew build` to verify full build passes
- [x] 5.2 Review generated API diff to ensure no unintended schema changes

## Additional Fixes

- [x] Fix `ModelConsoleEndpointTest` constructor to match updated `ModelConsoleEndpoint` signature (added `ProviderClientCache` parameter)
- [x] Mock `ProviderClientCache` and `AiProviderType` in `ModelConsoleEndpointTest` to satisfy `validateModel` endpoint type checks
