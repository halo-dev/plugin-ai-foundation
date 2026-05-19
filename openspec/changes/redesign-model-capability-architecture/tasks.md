## 1. Backend Model Profile Schema

- [x] 1.1 Replace `AiModel.spec.capabilities`, `endpointType`, and `supportedTextDelta` with structured profile fields for `modelType`, `features`, `adapterType`, and discovery evidence.
- [x] 1.2 Add backend enums or controlled value types for model types, features, adapter types, discovery source, and discovery confidence.
- [x] 1.3 Update backend validation to require a supported `modelType` and to reject unknown feature or adapter values.
- [x] 1.4 Update model creation and update tests for language, embedding, rerank, image-generation, invalid model type, invalid feature, and invalid adapter scenarios.

## 2. Provider Type And Discovery

- [x] 2.1 Extend `AiProviderType` and `ProviderTypeInfo` to expose supported model types, supported features, and supported adapter types.
- [x] 2.2 Replace endpoint recommendation logic with adapter recommendation based on provider type and model profile.
- [x] 2.3 Update `DiscoveredModel` and discovery response DTOs to return candidate model profile fields plus source and confidence.
- [x] 2.4 Update default OpenAI-compatible discovery to mark name-based inference as `source = rule` and `confidence = low`.
- [x] 2.5 Update provider type and discovery tests to cover OpenAI, OpenAI-compatible, embedding detection, language detection, and no-safe-adapter validation.

## 3. Default Model Slots

- [x] 3.1 Add Setting/ConfigMap-backed persistence for AI Foundation default model slots using `AiModel.metadata.name` values.
- [x] 3.2 Add backend APIs for reading and updating default language, embedding, rerank, and image-generation model slots.
- [x] 3.3 Validate that each configured default slot references an enabled model with the matching `modelType`.
- [x] 3.4 Add typed errors for missing default slots and incompatible default slot references.
- [x] 3.5 Add backend tests for valid slots, missing slots, disabled models, disabled providers, and wrong model type.

## 4. AiModelService Runtime Behavior

- [x] 4.1 Update `languageModel(modelName)` to require `modelType = language` before building a language wrapper.
- [x] 4.2 Update `embeddingModel(modelName)` to require `modelType = embedding` before building an embedding wrapper.
- [x] 4.3 Add default language and embedding wrapper resolution without exposing capability profile internals to consumer plugins.
- [x] 4.4 Update service tests for explicit model lookup, default lookup, modelName terminology, wrong model type, and existing disabled/error behavior.

## 5. Generated API Client

- [x] 5.1 Run backend OpenAPI generation after DTO and endpoint changes.
- [x] 5.2 Regenerate `ui/src/api/generated/`.
- [x] 5.3 Update frontend type imports to use generated profile, discovery, provider type, and default-slot DTOs.

## 6. Console Model Management UI

- [x] 6.1 Replace model capability checkbox UI with model type selection and feature selection.
- [x] 6.2 Hide `adapterType` from the normal model form while preserving advanced/debug visibility when needed.
- [x] 6.3 Update provider model discovery UI to edit candidate model type and features while preserving source and confidence metadata in the import payload.
- [x] 6.4 Allow admins to correct weak discovery profiles before importing discovered models.
- [x] 6.5 Update model list display and filters to use model type and features instead of flat capability tags.
- [x] 6.6 Add Console UI for default model slot selection with model-type-filtered selectors.

## 7. Cleanup And Compatibility Removal

- [x] 7.1 Remove old frontend constants and utilities that hardcode flat capability options or endpoint type labels.
- [x] 7.2 Remove backend parsing paths that map old string labels such as `chat` and `embedding` to endpoint recommendations.
- [x] 7.3 Update documentation and UI copy to distinguish `AiModel.metadata.name` from provider-side `spec.modelId`.
- [x] 7.4 Ensure no compatibility code remains for the unreleased old `capabilities`/`endpointType` schema.

## 8. Verification

- [x] 8.1 Run backend tests covering endpoint, provider type, discovery, default slots, and service behavior.
- [x] 8.2 Run frontend type-check and unit tests.
- [x] 8.3 Run `./gradlew generateApiClient` and verify generated client changes are committed with corresponding UI updates.
- [x] 8.4 Start the development environment and manually verify provider creation, manual model creation, discovery import, model filtering, and default model slot selection.
