## Why

Consumer plugins using `AiModelService.listModels()` cannot tell if a model is enabled or disabled, forcing them to call each model blindly. The console UI also cannot show when a provider was last connectivity-checked because `ProviderInfo` lacks `lastCheckedAt`. These gaps were identified in the code review as issue #18.

## What Changes

- Add `enabled` (boolean) to `ModelInfo` DTO in the `api` module so consumers know model availability
- Add `lastCheckedAt` (String/Instant) to `ProviderInfo` DTO so the UI can display last check time
- Update `AiModelServiceImpl.listModels()` to populate `enabled` from `AiModel.spec.enabled`
- Update `AiModelServiceImpl.listProviders()` to populate `lastCheckedAt` from `AiProvider.status.lastCheckedAt`
- Regenerate the TypeScript API client (`./gradlew generateApiClient`)
- Update `ProviderListItem.vue` and `ProviderDetail.vue` to display `lastCheckedAt`
- Update `ProviderModelListItem.vue` to use the `enabled` field from API (fallback to local spec until client is regenerated)

## Capabilities

### New Capabilities
- `model-provider-info-enrichment`: Expose `enabled` and `lastCheckedAt` in public API DTOs for model and provider discovery

### Modified Capabilities
- `ai-model-service`: `listModels()` and `listProviders()` now return additional fields (`enabled`, `lastCheckedAt` respectively). This is additive — no breaking changes to the public API surface.

## Impact

- `api/src/main/java/run/halo/aifoundation/ModelInfo.java`
- `api/src/main/java/run/halo/aifoundation/ProviderInfo.java`
- `app/src/main/java/run/halo/aifoundation/service/AiModelServiceImpl.java`
- `ui/src/api/generated/` (auto-regenerated)
- `ui/src/views/components/ProviderListItem.vue`
- `ui/src/views/ProviderDetail.vue`
- `ui/src/views/components/ProviderModelListItem.vue`
