## Context

The current discovery import path splits endpoint selection across backend and
frontend. Provider type implementations already declare `supportedEndpointTypes`
and infer model capabilities, but `ProviderModelsDiscoveryModal.vue` still calls a
frontend helper that chooses an endpoint type by checking whether capability and
endpoint strings contain `embedding` or `chat`.

That makes the Console UI aware of provider endpoint naming conventions. It also
means adding a future endpoint type, a non-English capability label, or a provider
with different endpoint naming can require frontend changes even though the provider
type system is meant to centralize provider behavior.

## Goals / Non-Goals

**Goals:**
- Make backend discovery results include endpoint information that can be submitted
  directly when creating models.
- Keep endpoint recommendation logic close to `AiProviderType` and its
  `supportedEndpointTypes`.
- Let model creation fill in a missing `endpointType` using backend provider type
  logic.
- Remove frontend string-based endpoint inference from the discovery import flow.

**Non-Goals:**
- No new provider type registry UI.
- No manual endpoint selector in the discovery import modal.
- No compatibility shim for the old frontend inference helper.
- No permission model changes.

## Decisions

### 1. Discovery API returns `suggestedEndpointType`

Each discovered model item will include a `suggestedEndpointType` value selected by
the backend for the provider resource being queried.

Alternative considered: return only a structured capability-to-endpoint map and keep
the final choice in Vue. That still leaves the UI responsible for provider semantics,
so it does not fully solve the issue.

### 2. Endpoint recommendation lives on provider type behavior

The implementation should add a provider-type-level recommendation path, with a
default implementation that maps known capabilities to the provider type's supported
endpoint types. Concrete provider types can override it when provider-specific rules
are needed.

The default should prefer an embedding endpoint for `EMBEDDING` capability and a chat
endpoint for `CHAT` capability, using `supportedEndpointTypes` as the authoritative
set. If no supported endpoint is suitable, backend validation should fail clearly
rather than silently creating a model with an invalid endpoint type.

Alternative considered: keep the recommendation helper inside
`ProviderConsoleEndpoint`. This would fix the immediate API shape but spread provider
behavior into endpoint code instead of the provider type system.

### 3. Model creation may omit `spec.endpointType`

`ModelConsoleEndpoint` should continue to validate endpoint support, but if
`spec.endpointType` is blank or absent it should resolve the provider resource,
find its provider type, and apply the provider type's default recommendation for
the model being created.

This makes API clients less fragile and gives the backend a consistent final guard
even when a client does not use discovery.

### 4. Frontend submits backend recommendation only

The discovery import modal should use the discovery payload's `suggestedEndpointType`
when building `AiModel.spec.endpointType`, or omit `endpointType` and rely on backend
defaulting if the recommendation is absent. It should no longer import or call a
frontend inference helper for this flow.

Generated client types should be refreshed after the backend response shape changes.

## Risks / Trade-offs

- [Risk] Discovery cannot confidently choose an endpoint for a future capability.
  → Mitigation: fail backend validation with a clear unsupported endpoint/defaulting
  message, and allow concrete provider types to override recommendation logic.
- [Risk] A model has both chat and embedding capabilities.
  → Mitigation: define the default priority explicitly; provider types can override
  when their API exposes better metadata.
- [Risk] UI may briefly depend on untyped discovery response fields until OpenAPI is
  regenerated.
  → Mitigation: run `./gradlew generateApiClient` as part of implementation after
  endpoint response changes.
- [Risk] Existing specs mention UI inference.
  → Mitigation: update `console-model-management` so the contract says the UI consumes
  backend recommendations instead of inferring endpoint type itself.
