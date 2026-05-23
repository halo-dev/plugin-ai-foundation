## 1. Backend DTO And Endpoint

- [x] 1.1 Add selector-oriented model option response DTOs for model identity, model profile, availability, and provider summary.
- [x] 1.2 Add a read-only `GET /model-options` Console endpoint under `console.api.aifoundation.halo.run/v1alpha1`.
- [x] 1.3 Aggregate `AiModel`, referenced `AiProvider`, and provider type registry metadata into model option items.
- [x] 1.4 Exclude Secret names, API keys, base URLs, proxy fields, raw provider config, and `adapterType` from the model option response.

## 2. Availability And Filtering

- [x] 2.1 Compute `available` and `unavailableReason` from model enabled state, provider existence, and provider enabled state.
- [x] 2.2 Return provider diagnostic phase and last checked time as context without using provider phase as an availability gate.
- [x] 2.3 Implement typed filters for `modelType`, `providerName`, `providerType`, `enabled`, `available`, `requiredFeatures`, and `keyword`.
- [x] 2.4 Validate unsupported model types, feature values, boolean values, and malformed filter input with 400 responses.
- [x] 2.5 Sort model options deterministically by provider display name, model display name, and model resource name.

## 3. Backend Tests

- [x] 3.1 Add endpoint tests for the aggregated model option response shape and provider display context.
- [x] 3.2 Add tests for available, model-disabled, provider-missing, and provider-disabled availability cases.
- [x] 3.3 Add tests proving provider diagnostic phase does not by itself make a model unavailable.
- [x] 3.4 Add filter tests for model type, provider name, provider type, enabled, available, required features, keyword, and invalid values.
- [x] 3.5 Add a test ensuring sensitive and internal provider/model fields are not exposed.

## 4. Generated Client

- [x] 4.1 Run backend OpenAPI generation after adding the endpoint and DTOs.
- [x] 4.2 Regenerate `ui/src/api/generated/` using the repo's API client generation workflow.
- [x] 4.3 Verify the generated client exposes the model options endpoint and response types.

## 5. Console UI Integration

- [x] 5.1 Add a frontend composable for fetching model options through the generated API client.
- [x] 5.2 Update default model slot selectors to use `model-options` with model-type and availability filtering.
- [x] 5.3 Render selector labels with model display name or model ID plus provider display context while preserving `AiModel.metadata.name` as the selected value.
- [x] 5.4 Keep provider-scoped model management lists on raw `/models?fieldSelector=spec.providerName=...`.
- [x] 5.5 Handle empty compatible option states and unavailable option display consistently in Chinese UI copy.

## 6. Verification

- [x] 6.1 Run backend tests covering the new endpoint and existing model/default-slot behavior.
- [x] 6.2 Run frontend type check and relevant unit tests.
- [x] 6.3 Run OpenSpec validation for `add-model-options-api`.
- [x] 6.4 Manually smoke test default model slot selection with multiple providers that expose same-named models.
- [x] 6.5 Confirm no changes were made to the Java Extension Point or public `AiModelService` API.
