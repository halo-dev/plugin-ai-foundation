## Why

Batch importing discovered models currently asks the Console UI to infer `endpointType`
from free-form capability strings and endpoint type names. This makes provider-specific
behavior leak into the frontend and forces new provider or endpoint semantics to be
handled in Vue code instead of the provider type system.

## What Changes

- Return a backend-recommended endpoint type for each discovered model from the provider
  model discovery API.
- Move discovery import endpoint selection out of the frontend string heuristic and into
  backend/provider-type logic.
- Allow backend model creation to fill a missing `spec.endpointType` with the provider
  type's default recommendation when the request omits it.
- Keep the Console UI focused on displaying discovery results and submitting the
  backend recommendation.

## Non-Goals

- Do not redesign the `AiProvider` or `AiModel` Extension schema beyond the endpoint type
  handling needed for this change.
- Do not introduce user-facing endpoint type selection in the discovery batch import flow.
- Do not add role or permission configuration; this remains a super-administrator tool.
- Do not preserve compatibility for the old frontend-only inference helper because the
  plugin has not been released.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `console-model-management`: Batch creation from discovered models will consume
  backend-recommended endpoint information instead of deriving endpoint type in the UI.
- `provider-type-registry`: Provider type behavior will include backend endpoint
  recommendation/defaulting for discovered models and missing model `endpointType`.

## Impact

- Backend API response shape for `GET /providers/{name}/discover-models`.
- Backend model creation validation/defaulting in `ModelConsoleEndpoint`.
- Provider type interface or support methods used to map capabilities to supported endpoint
  types.
- Frontend discovery import flow and generated API client usage after regeneration.
- Tests for provider discovery, model creation defaulting, and UI import payloads.
