## 1. API Module Changes

- [x] 1.1 Update `AiModelService` interface: change `languageModel(String modelRef)` and `embeddingModel(String modelRef)` parameter name to `modelName`, representing `AiModel.metadata.name`
- [x] 1.2 Update `ModelInfo` DTO: add `name` field (from `metadata.name`), keep `providerName`, `modelId`, and `displayName` for display purposes
- [x] 1.3 Remove `ModelNotFoundException` constructor/usage related to invalid `modelRef` format (the `/` separator validation)

## 2. Backend Implementation

- [x] 2.1 Update `AiModelServiceImpl`: replace `parseModelRef()` + `findAiModel()` with `client.fetch(AiModel.class, modelName)`, remove string parsing logic
- [x] 2.2 Update `AiModelServiceImpl.listModels()`: include `name` (from `metadata.name`) in returned `ModelInfo` objects
- [x] 2.3 Update `AiModelServiceImpl.languageModel()` and `embeddingModel()`: accept `metadata.name`, fetch model via `client.fetch`, then fetch provider via `model.getSpec().getProviderName()`
- [x] 2.4 Update `ModelNotFoundException`: remove format-specific constructor, simplify to accept model name string

## 3. Debug Endpoint Changes

- [x] 3.1 Update `ProviderDebugEndpoint`: change test-chat route from `POST /providers/{providerName}/models/{modelId}/test-chat` to `POST /models/{name}/test-chat`
- [x] 3.2 Update test-chat handler: accept `name` path variable, use `client.fetch(AiModel.class, name)` instead of constructing `modelRef`
- [x] 3.3 Update test-chat response: replace `modelRef` field with `modelName` field

## 4. Frontend Changes

- [x] 4.1 Update `ModelForm.vue`: when creating a new model, set `metadata.generateName` to `${providerName}-${modelId}-` instead of `model-`
- [x] 4.2 Update `TestChatModal.vue`: pass `metadata.name` instead of separate `providerName` and `modelId` props
- [x] 4.3 Update `ModelList.vue`: update test chat trigger to pass `model.metadata.name`
- [x] 4.4 Update `ModelDiscoveryModal.vue`: set `generateName` to `${providerName}-${modelId}-` when batch-adding discovered models

## 5. Validation

- [x] 5.1 Verify `(providerName, modelId)` uniqueness validation in `ModelConsoleEndpoint` still works correctly
- [x] 5.2 Build and run the plugin, test model creation with new `generateName` pattern
- [x] 5.3 Test `languageModel()` and `embeddingModel()` with `metadata.name`
- [x] 5.4 Test debug endpoint `POST /models/{name}/test-chat`
