## Why

Consumer plugins currently reference models via a composite `modelRef` string (`providerName/modelId`), which requires `list + filter` lookup and deviates from Halo's convention of using `metadata.name` for cross-extension references. Using `AiModel.metadata.name` as the model identifier aligns with the Halo ecosystem, enables O(1) `client.fetch` lookups, and simplifies debug endpoints.

## What Changes

- **BREAKING**: `AiModelService.languageModel(modelRef)` and `AiModelService.embeddingModel(modelRef)` parameter changes from composite `"providerName/modelId"` string to `AiModel.metadata.name`
- **BREAKING**: `ModelNotFoundException` thrown for missing `/` separator is no longer needed; validation changes to a simple existence check
- Change `AiModel` creation to set `generateName` to `${providerName}-${modelId}-` so that `metadata.name` is human-identifiable (e.g., `openai-official-gpt-4o-a7f3k`)
- Internal model lookup changes from `list + filter` by `(spec.providerName, spec.modelId)` to `client.fetch(AiModel.class, name)` — O(1)
- `ModelInfo` DTO: replace `providerName` + `modelId` fields with `name` (the `metadata.name`)
- Test chat debug endpoint simplifies from `POST /providers/{providerName}/models/{modelId}/test-chat` to `POST /models/{name}/test-chat`

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `ai-model-service`: model reference format changes from `providerName/modelId` to `metadata.name`; lookup strategy changes; `ModelInfo` shape changes; exception hierarchy simplified
- `provider-debug-api`: test-chat endpoint path changes from provider+model composite to single model name parameter

## Impact

- **API breaking change**: Any consumer plugin currently calling `aiModelService.languageModel("provider/model")` must update to pass `metadata.name`
- **Backend**: `AiModelServiceImpl`, `ModelConsoleEndpoint`, `ProviderDebugEndpoint` all affected
- **Frontend**: Model creation form must set `generateName` to `${providerName}-${modelId}-`; test chat modal must pass `metadata.name` instead of separate `providerName`/`modelId`
- **OpenSpec specs**: `ai-model-service/spec.md` and `provider-debug-api/spec.md` need delta updates
