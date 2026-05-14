## 1. API Module — Add ModelDisabledException

- [x] 1.1 Create `ModelDisabledException` class in `api/` module following the same pattern as `ProviderDisabledException`

## 2. Service Implementation — Check Model Enabled Status

- [x] 2.1 Add `enabled` check in `AiModelServiceImpl.languageModel()` after fetching the `AiModel`
- [x] 2.2 Add `enabled` check in `AiModelServiceImpl.embeddingModel()` after fetching the `AiModel`

## 3. Verification

- [x] 3.1 Run `./gradlew test` to ensure existing tests pass
- [x] 3.2 Verify `ModelDisabledException` is thrown when requesting a disabled model
