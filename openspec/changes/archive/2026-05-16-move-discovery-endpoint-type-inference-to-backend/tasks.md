## 1. Backend Endpoint Recommendation

- [x] 1.1 Add provider-type-level endpoint recommendation behavior based on discovered model capabilities and supported endpoint types.
- [x] 1.2 Return `suggestedEndpointType` for discovered models from the provider model discovery endpoint.
- [x] 1.3 Ensure recommended endpoint values always come from the provider type's `supportedEndpointTypes`.

## 2. Model Creation Defaulting and Validation

- [x] 2.1 Update model creation validation to resolve the referenced provider before endpoint validation.
- [x] 2.2 Default missing or blank `spec.endpointType` with the provider type recommendation.
- [x] 2.3 Reject model creation when no supported endpoint type can be recommended.
- [x] 2.4 Preserve existing validation that rejects explicitly unsupported endpoint types.

## 3. Frontend Discovery Import

- [x] 3.1 Regenerate the TypeScript API client after backend API response changes.
- [x] 3.2 Update the discovery model type and import flow to use `suggestedEndpointType`.
- [x] 3.3 Remove frontend endpoint inference from the discovery import path.
- [x] 3.4 Remove obsolete frontend inference tests or replace them with tests for backend recommendation consumption.

## 4. Verification

- [x] 4.1 Add or update backend tests for discovery responses with chat and embedding recommendations.
- [x] 4.2 Add or update backend tests for missing `endpointType` defaulting and unsupported recommendation validation.
- [x] 4.3 Add or update frontend tests for batch import payload construction.
- [x] 4.4 Run backend and frontend verification commands relevant to the touched modules.
