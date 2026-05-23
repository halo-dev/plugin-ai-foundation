## Why

The current `/models` endpoint returns raw `AiModel` resources, which is useful for CRUD but awkward for selection UIs. Default model settings, AI Foundation Console surfaces, and other plugins' configuration pages need a model list that already includes model display metadata, provider display metadata, availability, and filtering semantics.

## What Changes

- Add a read-only Console API for aggregated model options, tentatively `GET /apis/console.api.aifoundation.halo.run/v1alpha1/model-options`.
- Return model selector DTOs that join `AiModel`, its referenced `AiProvider`, and provider type registry metadata.
- Support server-side filtering for model type, provider resource name, provider type, availability, enabled state, required features, and keyword search.
- Define model availability for selector purposes as model enabled, provider exists, and provider enabled; provider diagnostic phase is returned as context but does not by itself hide a model.
- Update AI Foundation Console model selectors, especially default model slots, to use the aggregated option endpoint instead of client-side joining raw `/models` results with provider metadata.
- Keep `/models` as the raw `AiModel` resource management endpoint for CRUD and provider-scoped admin lists.

## Capabilities

### New Capabilities

- `model-options-api`: Defines the aggregated read-only model option endpoint, response DTO, availability semantics, filtering, and sorting.

### Modified Capabilities

- `default-model-slots`: Default-slot selectors SHALL use model options so each option can display model and provider display names while preserving model-type eligibility.
- `console-model-management`: Console selector-style model lists SHALL use the aggregated option shape when provider display context or availability is needed.

## Non-Goals

- Do not modify the Java Extension Point or public `AiModelService` API in this change.
- Do not replace `/models` or change its raw `AiModel` CRUD/list contract.
- Do not expose secrets, provider base URLs, proxy settings, adapter internals, or other sensitive/advanced provider configuration through model options.
- Do not add role-specific permission configuration; this remains a super-admin oriented Console API.
- Do not introduce runtime failover, automatic model recommendation, pricing, quota, or usage governance.

## Impact

- Backend Console API: new read-only endpoint and DTOs under `console.api.aifoundation.halo.run/v1alpha1`.
- Backend aggregation logic: joins configured models with providers and provider type metadata.
- Backend filtering: validates typed query parameters and applies efficient filtering where indexed fields already exist.
- OpenAPI and generated frontend client: `./gradlew generateApiClient` and `pnpm -C ui api-client:gen` if required by the repo workflow.
- Frontend Console UI: default model slot selectors and any model picker surfaces that need provider display context.
- Existing Java SDK/API module: intentionally unchanged.
