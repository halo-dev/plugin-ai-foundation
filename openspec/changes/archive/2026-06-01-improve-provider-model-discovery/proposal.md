## Why

Provider model discovery currently relies mostly on a default OpenAI-compatible `GET /v1/models` flow and low-confidence model-name heuristics. This loses or misclassifies models when a provider exposes typed discovery metadata, typed query parameters, or provider-specific model-list endpoints, especially for embedding and rerank models.

## What Changes

- Improve provider model discovery so each built-in provider first uses an official provider-specific model-list API when that API can expose model type, feature, or grouping information.
- Keep the default OpenAI-compatible `/v1/models` discovery path as fallback for providers without a useful typed model-list API and for `openailike`.
- Normalize provider-specific model metadata into the existing `DiscoveredModel` fields: `modelType`, `features`, `adapterType`, `source`, and `confidence`.
- Reuse the existing `source` and `confidence` fields to distinguish remote-confirmed profiles from low-confidence rule-based inference; do not add response fields in this change.
- Keep administrator correction in the Console discovery-import flow even when the backend returns high-confidence remote metadata.
- Group discovered models in the Console by confirmed model type, with low-confidence rule-derived profiles shown in a "needs confirmation" group.
- Add Ollama embedding adapter support to provider metadata because the provider can build embedding models today, while still avoiding static model-name catalog inference.

Non-goals:

- Do not maintain provider-specific static model catalogs or hardcoded model-name lists.
- Do not auto-classify models such as `BAAI/bge-m3` as embedding unless the remote API or typed endpoint context confirms that type, or the existing generic low-confidence rule matches.
- Do not extend `ProviderModelDiscoveryResponse` with warnings or diagnostics in this change.
- Do not add discovery support for providers that are not already implemented in this plugin.
- Do not change `openailike` beyond preserving the default OpenAI-compatible fallback behavior.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `provider-type-registry`: Provider type model discovery SHALL prefer official provider-specific typed discovery where available, preserve default fallback where not, and expose normalized model profiles through existing fields.
- `model-capability-profile`: Discovered model profiles SHALL distinguish remote-confirmed metadata from low-confidence rules using existing source/confidence semantics and SHALL not depend on static provider catalogs.
- `console-model-management`: The discovery-import UI SHALL group discovered models by confirmed type while preserving correction and cross-group batch import.

## Impact

- Backend provider classes under `app/src/main/java/run/halo/aifoundation/provider/`.
- Shared discovery helpers in `AbstractAiProviderType` and related provider support records/enums.
- Provider and endpoint tests for model discovery parsing, adapter recommendations, and source/confidence semantics.
- Console discovery modal in `ui/src/views/components/ProviderModelsDiscoveryModal.vue` and related model utility tests.
- No OpenAPI response-shape change is expected; generated API client regeneration should not be required unless implementation uncovers an existing schema mismatch.
