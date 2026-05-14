## Why

`AiModelServiceImpl` currently checks whether the parent `AiProvider` is enabled before returning a `LanguageModel` or `EmbeddingModel`, but it never validates the `AiModel.spec.enabled` flag. A disabled model can still be resolved and invoked by consumer plugins, which violates the intended behavior of the enabled toggle.

## What Changes

- Add `ModelDisabledException` in the `api/` module (thrown when a model exists but `spec.enabled == false`).
- Update `AiModelServiceImpl.languageModel()` to check `aiModel.getSpec().isEnabled()` after fetching the model.
- Update `AiModelServiceImpl.embeddingModel()` to perform the same check.
- Update `ai-model-service` spec to document the new model-level enabled check.

## Capabilities

### New Capabilities
- *(none — this is a behavioral fix on existing capability)*

### Modified Capabilities
- `ai-model-service`: The service SHALL now verify both `AiProvider.spec.enabled` **and** `AiModel.spec.enabled` before returning a model instance. The existing `ProviderDisabledException` behavior for disabled providers is unchanged; a new `ModelDisabledException` is thrown when the model itself is disabled.

## Impact

- `api/` module: new exception class `ModelDisabledException`
- `app/` module: `AiModelServiceImpl.java` (two method bodies)
- `openspec/specs/ai-model-service/spec.md`: update scenario requirements
