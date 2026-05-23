## Context

`GET /models` currently exposes raw `AiModel` resources for model management. That endpoint is appropriate for CRUD, provider-scoped administration, and Extension-shaped data, but it leaves selector UIs to assemble display context themselves:

- `AiModel.spec.providerName` is only the provider resource name, not the provider display name.
- Provider display metadata lives on `AiProvider.spec`.
- Provider type display metadata, icon URLs, and supported profile metadata live in the provider type registry.
- Default model slot selectors need only eligible, selectable options with enough provider context to distinguish same-named models.

This change introduces a read-only aggregation endpoint for Console and UI integration use cases while preserving the raw `/models` contract.

## Goals / Non-Goals

**Goals:**

- Provide a single backend-owned model option shape for selectors and plugin configuration UIs.
- Include enough model, provider, provider type, capability profile, and availability metadata to render clear Chinese Console labels.
- Support typed filters commonly needed by default slots, model pickers, and future feature-based selectors.
- Keep availability semantics simple and deterministic.
- Keep raw resource management and selector presentation as separate API contracts.

**Non-Goals:**

- Do not modify the Java Extension Point or public `AiModelService` API.
- Do not expose adapter internals, secrets, base URLs, proxy settings, or other provider configuration details.
- Do not make provider diagnostic phase a hard availability gate.
- Do not turn this into model recommendation, failover, quota, pricing, or usage governance.
- Do not replace `/models` for provider detail CRUD or admin model management.

## Decisions

### 1. Add `model-options` instead of enriching `/models`

`/models` should remain a raw `AiModel` resource endpoint. Enriching it with provider display fields would mix Extension resource representation with a UI projection and would make CRUD consumers wonder which fields are persisted.

`/model-options` is explicitly a read-only projection. Its fields are derived from:

```text
AiModel.spec.providerName ──fetch──> AiProvider
       │                              │
       │                              └── provider.spec.providerType
       │                                      │
       └──────────── join ───────────────────▼
                                 ProviderTypeInfo registry
```

Alternative considered: let every UI request `/models`, `/providers`, and `/provider-types` and join client-side. This was rejected because it duplicates availability rules and display fallback logic across AI Foundation and consumer plugin UIs.

### 2. Keep the response selector-oriented

The endpoint returns `ModelOption` items, not `AiModel` resources. The shape should be stable for selection:

- model identity: `name`, `modelId`, `displayName`
- model profile: `modelType`, `features`
- model state: `enabled`, `available`, `unavailableReason`
- provider summary: provider resource name, provider display name, provider type, provider type display name, icon URL, enabled state, diagnostic phase, last checked time

The option MUST NOT include API keys, Secret names, base URL, proxy fields, or `adapterType`.

Alternative considered: include the full nested `AiModel` and `AiProvider`. This was rejected because selectors do not need the full resources and full nesting makes it too easy to leak management-only fields.

### 3. Define `available` narrowly

For selector purposes, a model is available when:

- the `AiModel` is enabled,
- the referenced `AiProvider` exists,
- the referenced `AiProvider` is enabled.

Provider diagnostic phase is returned as context but is not a hard gate. `UNKNOWN` often means connectivity has not been checked yet, and `ERROR` may be stale or transient. Invocation paths still surface real provider errors at runtime.

Alternative considered: require provider `status.phase = OK`. This was rejected because it would hide usable models before an admin runs diagnostics, and it would make stale diagnostic state control selection.

### 4. Use typed query parameters instead of Halo field selectors

`/models` already supports Halo `fieldSelector` and `labelSelector` for raw resources. `model-options` should expose selector-oriented filters:

- `modelType`
- `providerName`
- `providerType`
- `enabled`
- `available`
- `requiredFeatures`
- `keyword`

`requiredFeatures` uses all-of semantics because "needs a tool-capable streaming language model" should not match a model that supports only one requested feature.

Alternative considered: forward arbitrary field selectors to the joined projection. This was rejected because joined data includes derived provider/type fields that are not indexed Extension fields and because selector clients benefit from a smaller, documented filter set.

### 5. Prefer backend aggregation, then frontend simplicity

The backend should perform the join and availability calculation. The frontend should use generated API clients and render labels from the option DTO. Default slot selectors can then filter by `modelType` and `available=true` with one request per slot group or one broad request reused across slots.

Alternative considered: keep default slots on raw `/models` and only use model options for third-party plugin surfaces. This was rejected because default slots are the primary in-repo example of why the endpoint exists.

## Risks / Trade-offs

- [Risk] The endpoint duplicates some fields already visible through `/models`, `/providers`, and `/provider-types`. -> Mitigation: document it as a read-only projection and keep raw CRUD unchanged.
- [Risk] Missing provider references could occur if data is inconsistent. -> Mitigation: return the model as unavailable with a clear reason instead of failing the whole list.
- [Risk] Filtering by provider type requires joining providers before filtering. -> Mitigation: keep the expected model/provider counts small for Console use; use indexed `spec.providerName` when `providerName` is supplied.
- [Risk] Clients may treat `available=true` as a guarantee of successful invocation. -> Mitigation: define availability only as local configuration eligibility; runtime provider errors still surface during invocation.
- [Risk] The endpoint could grow into a general capability-query API. -> Mitigation: keep this change limited to Console/HTTP selector use cases and explicitly leave Java Extension unchanged.

## Migration Plan

1. Add backend DTOs and a read-only Console endpoint for `model-options`.
2. Implement aggregation from `AiModel`, `AiProvider`, and provider type registry metadata.
3. Add typed query parsing and validation for supported filters.
4. Add endpoint tests for response shape, availability reasons, filtering, sorting, and sensitive field exclusion.
5. Regenerate the OpenAPI TypeScript client.
6. Update default model slot selectors and relevant Console picker utilities to use the generated model option client.
7. Keep `/models` and the Java Extension Point unchanged.

Rollback is simple while unreleased: revert the change branch. No persisted data migration is required.

## Open Questions

- Should the first UI pass request one broad `available=true` option list and partition by slot type client-side, or request per `modelType` for each slot?
- Should `unavailableReason` use stable enum values immediately, or start as string constants generated into the TypeScript client?
