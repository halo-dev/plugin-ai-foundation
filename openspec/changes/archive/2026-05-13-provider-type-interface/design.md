## Context

Currently, provider types (openai, deepseek, ollama, etc.) are hardcoded as string constants in two separate places: `AiProvider.SUPPORTED_PROVIDER_TYPES` (Java) and `SUPPORTED_PROVIDER_TYPES` / `PROVIDER_TYPE_LABELS` / `BUILT_IN_PROVIDERS` (TypeScript). The `ProviderAdapterFactory` uses a switch statement to dispatch to the right adapter class. Adding a new provider requires editing 12+ files across backend and frontend in sync.

The existing adapter pattern splits each provider into two concerns: `ProviderAdapter` (behavior) and constant lists (identity/metadata). This split is artificial — a provider's identity, metadata, and behavior are inherently one concept.

## Goals / Non-Goals

**Goals:**
- One class per provider that encapsulates identity, metadata, and behavior
- Runtime discovery of available provider types via Spring IoC
- REST API exposing provider type metadata so frontend can render dynamically
- Eliminate all hardcoded provider type constant lists in both Java and TypeScript
- Cleaner architecture where adding a new provider = one new class + `@Component`

**Non-Goals:**
- Third-party plugin extensibility via the `AiProviderType` interface
- Dynamic FormKit form schemas per provider type
- Changes to `AiModel` Extension or model management
- Backward compatibility with the old adapter classes (plugin is unreleased)

## Decisions

### Decision 1: Merge ProviderAdapter into AiProviderType

**Choice:** Single `AiProviderType` interface + `AbstractAiProviderType` base class, replacing both `ProviderAdapter`/`AbstractProviderAdapter` and the constant lists.

**Alternative:** Keep `ProviderAdapter` and add a separate `AiProviderType` metadata interface. Each provider would be two classes.

**Rationale:** A provider is one concept. Splitting it forces synchronization of `providerType` strings across two files. Merging eliminates that. The halo-pro `PaymentMethodProvider` pattern validates this — one interface per payment method, one `@Component` class per implementation.

### Decision 2: Stateless behavior methods (parameters, not fields)

**Choice:** Behavior methods like `buildChatModel(AiProvider provider, String apiKey)` receive context as parameters rather than holding it as instance state.

**Alternative:** Keep the current pattern where adapters are instantiated with provider/apiKey in the constructor.

**Rationale:** This enables `AiProviderType` beans to be singletons (`@Component`). The `ProviderClientCache` already caches the constructed `ChatModel`/`EmbeddingModel` instances — there is no need for the intermediate adapter to also hold state. Following halo-pro's pattern where `initiatePayment(context, exchange)` receives context as parameters.

### Decision 3: Structured metadata fields, not FormKit schemas

**Choice:** Each `AiProviderType` declares structured metadata (`requiresBaseUrl()`, `getDefaultBaseUrl()`, `getSupportedEndpointTypes()`, `supportsEmbeddings()`, `isBuiltIn()`) rather than returning a FormKit JSON schema.

**Alternative:** Each provider type returns a `getConfigFormSchema()` FormKit schema, allowing arbitrary form customization.

**Rationale:** Current config differences between providers are small: only `requiresBaseUrl` and `supportedEndpointTypes` vary. A FormKit schema system is over-engineering for this level of variance. If a future provider needs truly unique config fields, the unused `spec.config` map or a schema system can be introduced then.

### Decision 4: REST endpoint for provider type metadata

**Choice:** Add `GET /apis/console.aifoundation.halo.run/v1alpha1/provider-types` returning a list of `ProviderTypeInfo` DTOs with all metadata fields.

**Rationale:** The frontend must know available types, their display names, default URLs, and endpoint types. Currently these are hardcoded TypeScript constants. An API endpoint eliminates the need for any frontend constant list and ensures frontend always reflects the runtime state of the backend.

### Decision 5: Remove unused `spec.config` map

**Choice:** Remove the `Map<String, String> config` field from `AiProviderSpec`.

**Rationale:** It was declared but never read or written by any code in the project. Removing it simplifies the schema. If provider-specific configuration is needed in the future, it can be re-added with a clear purpose.

## Risks / Trade-offs

**[Risk] Larger PR with many file changes** → All 9 adapter classes must be rewritten simultaneously. Mitigation: The logic within each adapter is preserved; only the class structure changes. Each new provider type class is a straightforward mechanical transformation of its old adapter.

**[Risk] `ProviderClientCache` must be refactored** → Currently caches `ProviderAdapterHolder` which wraps a `ProviderAdapter`. Must be updated to cache `ChatModel`/`EmbeddingModel` directly, looking up `AiProviderType` by name. Mitigation: Cache logic is localized to one file.

**[Risk] Frontend must add API call on mount** → Provider form must fetch provider types from API before rendering the dropdown. Mitigation: This is a standard pattern (same as halo-pro's payment method creation modal). The data is small and cacheable.
