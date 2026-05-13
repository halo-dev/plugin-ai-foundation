## Why

Provider types (openai, deepseek, ollama, etc.) are hardcoded as string constants in 12+ locations across Java and TypeScript. Adding a new provider requires editing both backend and frontend in sync — a fragile, error-prone process. The metadata about each provider type (display name, default baseUrl, supported endpoint types) should be self-describing so that the system can discover available providers at runtime and the frontend can render dynamically without maintaining parallel constant lists.

## What Changes

- **Introduce `AiProviderType` interface** — a single Spring `@Component` per provider that encapsulates identity, display metadata, configuration metadata, and behavior (model building, model discovery). One class per provider, replacing the current split between `ProviderAdapter` and hardcoded constant lists.
- **Merge `ProviderAdapter` into `AiProviderType`** — adapter methods (`buildChatModel`, `buildEmbeddingModel`, `discoverModels`, etc.) become methods on the provider type. Behavior methods receive `AiProvider` + `apiKey` as parameters rather than holding them as state, enabling single-instance `@Component` beans.
- **Add REST endpoint** — `GET /apis/.../provider-types` returns metadata for all discovered `AiProviderType` beans, enabling the frontend to render dynamically.
- **Eliminate hardcoded constants** — remove `SUPPORTED_PROVIDER_TYPES`, `PROVIDER_TYPE_LABELS`, `BUILT_IN_PROVIDERS` from both Java and TypeScript. Remove the `ProviderAdapterFactory` switch statement. Frontend consumes provider type metadata entirely from the API.
- **BREAKING**: Replace `ProviderAdapter` / `ProviderAdapterFactory` / `AbstractProviderAdapter` with the new `AiProviderType` / `AbstractAiProviderType` hierarchy. All 9 existing adapter classes are rewritten as provider type classes.

## Capabilities

### New Capabilities
- `provider-type-registry`: Self-describing provider type system where each provider is a single `@Component` class that provides identity, metadata, and behavior. Includes Spring IoC discovery and a REST endpoint exposing type metadata.

### Modified Capabilities
- `ai-provider-config`: Provider creation validation and frontend form rendering will shift from hardcoded constant lists to dynamic API-driven metadata. The `ProviderAdapterFactory` switch dispatch is replaced by `AiProviderType.createAdapter()`-style methods. The `config` map field (currently unused) may be removed.

## Impact

- **Backend**: All 9 adapter classes (`OpenAiAdapter`, `DeepSeekAdapter`, etc.) rewritten; `ProviderAdapterFactory`, `AbstractProviderAdapter`, `ProviderAdapter` interface removed; `ProviderClientCache` updated to work with new types; `ProviderConsoleEndpoint` updated for validation and new provider-types endpoint.
- **Frontend**: `SUPPORTED_PROVIDER_TYPES`, `PROVIDER_TYPE_LABELS`, `BUILT_IN_PROVIDERS` constants removed; `ProviderForm.vue`, `ModelDiscoveryModal.vue`, `ProviderList.vue`, `ProviderDetail.vue` refactored to consume API-driven metadata.
- **API**: New `GET /provider-types` endpoint. No changes to existing `AiProvider` / `AiModel` Extension schemas.

## Non-goals

- Third-party plugin extensibility — the interface is internal architecture, not a public extension point.
- FormKit dynamic form schemas — provider config differences are small enough that structured metadata fields (`requiresBaseUrl`, `defaultBaseUrl`, `supportedEndpointTypes`) suffice.
- Changes to `AiModel` Extension or model management UI.
