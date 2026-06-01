## Purpose

Define how AI provider types declare metadata, runtime behavior, discovery support, and endpoint recommendations.
## Requirements
### Requirement: AiProviderType interface definition
The system SHALL define a `AiProviderType` interface that encapsulates identity, display metadata, configuration metadata, supported model profile metadata, and behavior for an AI provider type.

#### Scenario: Interface contract
- **WHEN** a class implements `AiProviderType`
- **THEN** it MUST provide `getProviderType()` returning a unique string identifier
- **AND** it MUST provide `getDisplayName()` returning a human-readable name
- **AND** it MUST provide `isBuiltIn()` indicating whether the provider has built-in defaults
- **AND** it MUST provide `requiresBaseUrl()` indicating whether the user must supply a base URL
- **AND** it MUST provide `getDefaultBaseUrl()` returning the default base URL (may be null if requiresBaseUrl is true and no default exists)
- **AND** it MUST provide supported model type metadata for model types such as `language`, `embedding`, `rerank`, or `image-generation`
- **AND** it MUST provide supported adapter type metadata for internal adapters such as `openai-chat`, `openai-embedding`, `openai-image`, or provider-specific equivalents
- **AND** it MUST provide supported feature metadata when the provider type can declare model features such as `streaming`, `vision`, `tool-call`, or `structured-output`
- **AND** it MUST NOT require the Console to hardcode provider-type-to-model-profile mappings

### Requirement: AiProviderType display metadata
Each `AiProviderType` SHALL provide optional display metadata for UI rendering.

#### Scenario: Optional metadata fields
- **WHEN** a `AiProviderType` implementation provides display metadata
- **THEN** it MAY provide `getIconUrl()` returning a path or URL to a provider logo
- **AND** it MAY provide `getDocumentationUrl()` returning a URL to the provider's API documentation
- **AND** it MAY provide `getWebsiteUrl()` returning a URL to the provider's website
- **AND** it MAY provide `getDescription()` returning a short description of the provider

### Requirement: AiProviderType behavior methods
Each `AiProviderType` SHALL provide methods for building AI models and discovering available models. Behavior methods MUST receive `AiProvider` and API key as parameters rather than holding them as instance state.

#### Scenario: Chat model construction
- **WHEN** `buildChatModel(provider, apiKey)` is called on an `AiProviderType`
- **THEN** it SHALL return a `ChatModel` instance configured for that provider
- **AND** it MAY return null if the provider does not support chat

#### Scenario: Embedding model construction
- **WHEN** `buildEmbeddingModel(provider, apiKey)` is called on an `AiProviderType`
- **THEN** it SHALL return an `EmbeddingModel` instance if `supportsEmbeddings()` is true
- **AND** it SHALL return null if `supportsEmbeddings()` is false

#### Scenario: Model discovery
- **WHEN** `discoverModels(provider, apiKey)` is called on an `AiProviderType`
- **THEN** it SHALL return a list of `DiscoveredModel` instances available from the provider's API

#### Scenario: Embedding batch limits
- **WHEN** `maxEmbeddingsPerCall()` is called on an `AiProviderType`
- **THEN** it SHALL return the maximum number of embeddings per API call for that provider

#### Scenario: Parallel call support
- **WHEN** `supportsParallelCalls()` is called on an `AiProviderType`
- **THEN** it SHALL return whether the provider supports concurrent embedding calls

### Requirement: Provider embedding invocation behavior
Provider type implementations SHALL keep provider-specific embedding request mapping behind the provider type system and SHALL NOT require generic embedding service code to branch on concrete provider names.

#### Scenario: Provider applies supported embedding options
- **WHEN** an embedding request includes namespaced provider options for the current provider
- **THEN** the provider embedding implementation SHALL map supported options to the underlying provider request
- **AND** unsupported options SHALL be surfaced as warnings when the request otherwise succeeds

#### Scenario: Provider applies request headers
- **WHEN** an embedding request includes request-scoped headers
- **THEN** the provider embedding implementation SHALL apply those headers when its underlying adapter supports per-request headers
- **AND** unsupported per-request headers SHALL be surfaced as warnings

#### Scenario: Provider response diagnostics mapped
- **WHEN** a provider embedding response includes usage, response id, model, headers, or provider-native metadata
- **THEN** the provider embedding implementation SHALL map safe diagnostics into provider-neutral response fields

#### Scenario: Generic service remains provider-neutral
- **WHEN** a new provider type is added
- **THEN** embedding provider-specific option mapping SHALL be implemented in that provider type or its provider-owned helper
- **AND** `EmbeddingModelImpl` SHALL NOT need provider-specific conditionals for that provider

### Requirement: AbstractAiProviderType base class
The system SHALL provide an `AbstractAiProviderType` base class that supplies default implementations for common behavior.

#### Scenario: Default model discovery
- **WHEN** a provider type extends `AbstractAiProviderType` and does not override `discoverModels`
- **THEN** the base class SHALL provide a default implementation that queries the provider's `/v1/models` endpoint
- **AND** model type inference based only on model ID rules SHALL use `source = rule` and `confidence = low`

#### Scenario: Default base URL resolution
- **WHEN** `resolveBaseUrl(provider)` is called
- **THEN** it SHALL return `provider.spec.baseUrl` if set, otherwise `getDefaultBaseUrl()`

#### Scenario: Default embedding model behavior
- **WHEN** a provider type extends `AbstractAiProviderType` and `supportsEmbeddings()` returns false
- **THEN** `buildEmbeddingModel()` SHALL return null by default

#### Scenario: Default batch limits
- **WHEN** a provider type extends `AbstractAiProviderType` and does not override `maxEmbeddingsPerCall()`
- **THEN** it SHALL return 96 by default
- **AND** `supportsParallelCalls()` SHALL return true by default

### Requirement: Spring IoC provider type discovery
The system SHALL discover all `AiProviderType` beans via Spring `ApplicationContext.getBeansOfType()`.

#### Scenario: All provider types discovered
- **WHEN** the application starts
- **THEN** all `@Component` classes implementing `AiProviderType` SHALL be registered in the Spring context
- **AND** they SHALL be discoverable via `ApplicationContext.getBeansOfType(AiProviderType.class)`

#### Scenario: Provider type lookup by name
- **WHEN** the system needs the `AiProviderType` for provider type string "openai"
- **THEN** it SHALL find the registered bean where `getProviderType()` returns "openai"

#### Scenario: Duplicate provider type detection
- **WHEN** two `AiProviderType` beans return the same `getProviderType()` string
- **THEN** the system SHALL log a warning and the duplicate registration behavior is undefined

### Requirement: Provider type metadata REST endpoint
The system SHALL expose a console API endpoint returning metadata for all discovered provider types.

#### Scenario: List all provider types
- **WHEN** a client sends `GET /apis/console.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** the system SHALL return a list of `ProviderTypeInfo` objects
- **AND** each object SHALL include `providerType`, `displayName`, `description`, `iconUrl`, `documentationUrl`, `websiteUrl`, `builtIn`, `requiresBaseUrl`, and `defaultBaseUrl`
- **AND** each object SHALL include supported model type metadata
- **AND** each object SHALL include supported adapter type metadata
- **AND** each object SHALL include supported feature metadata when available

#### Scenario: Provider types ordered
- **WHEN** the provider types list is returned
- **THEN** built-in provider types SHALL appear before non-built-in types
- **AND** within each group, types SHALL be ordered alphabetically by `providerType`

### Requirement: Concrete provider type implementations
The system SHALL provide `AiProviderType` implementations for all currently supported provider types: `openai`, `aihubmix`, `deepseek`, `siliconflow`, `doubao`, `ernie`, `zhipuai`, `ollama`, `openailike`.

#### Scenario: OpenAI provider type metadata
- **WHEN** the OpenAI provider type is queried
- **THEN** `getProviderType()` SHALL return "openai"
- **AND** `getDisplayName()` SHALL return "OpenAI"
- **AND** `isBuiltIn()` SHALL return true
- **AND** `requiresBaseUrl()` SHALL return false
- **AND** `getDefaultBaseUrl()` SHALL return "https://api.openai.com"
- **AND** supported model types SHALL include `language` and `embedding`
- **AND** supported adapter types SHALL include `openai-chat` and `openai-embedding`

#### Scenario: Ollama provider type metadata
- **WHEN** the Ollama provider type is queried
- **THEN** `getProviderType()` SHALL return "ollama"
- **AND** `getDisplayName()` SHALL return "Ollama"
- **AND** `isBuiltIn()` SHALL return false
- **AND** `requiresBaseUrl()` SHALL return true
- **AND** `getDefaultBaseUrl()` SHALL return "http://localhost:11434"
- **AND** supported model types SHALL include `language` and `embedding`
- **AND** supported adapter types SHALL include `ollama-chat` and `ollama-embedding`

#### Scenario: OpenAI-Like provider type metadata
- **WHEN** the OpenAI-Like provider type is queried
- **THEN** `getProviderType()` SHALL return "openailike"
- **AND** `getDisplayName()` SHALL return "OpenAI Compatible"
- **AND** `isBuiltIn()` SHALL return false
- **AND** `requiresBaseUrl()` SHALL return true
- **AND** `getDefaultBaseUrl()` SHALL return null
- **AND** supported model types SHALL include `language` and `embedding`
- **AND** supported adapter types SHALL include `openai-chat` and `openai-embedding`

#### Scenario: DeepSeek provider type metadata
- **WHEN** the DeepSeek provider type is queried
- **THEN** `getProviderType()` SHALL return "deepseek"
- **AND** `getDisplayName()` SHALL return "DeepSeek"
- **AND** `isBuiltIn()` SHALL return true
- **AND** `requiresBaseUrl()` SHALL return false
- **AND** `getDefaultBaseUrl()` SHALL return "https://api.deepseek.com"
- **AND** supported model types SHALL include `language`
- **AND** supported adapter types SHALL include `openai-chat`

#### Scenario: AiHubMix provider-specific behavior
- **WHEN** an `AiProvider` has `providerType = "aihubmix"`
- **THEN** the runtime SHALL use the built-in AiHubMix default baseUrl
- **AND** the runtime SHALL apply the provider-specific request header behavior required by AiHubMix

#### Scenario: AiHubMix typed model discovery
- **WHEN** model discovery is called for an AiHubMix provider
- **THEN** the provider type SHALL use AiHubMix model metadata fields such as model type and feature fields when available
- **AND** it SHALL map supported language and embedding profiles to AI Foundation model types and adapter types without using a static model catalog

#### Scenario: DouBao non-standard API paths
- **WHEN** an `AiProvider` has `providerType = "doubao"`
- **THEN** the language adapter SHALL use the `/v3/chat/completions` path
- **AND** the embedding adapter SHALL use the `/v3/embeddings` path

#### Scenario: Ernie non-standard API paths
- **WHEN** an `AiProvider` has `providerType = "ernie"`
- **THEN** the language adapter SHALL use the `/v2/chat/completions` path
- **AND** the embedding adapter SHALL use the `/v2/embeddings` path

#### Scenario: ZhiPu non-standard API paths
- **WHEN** an `AiProvider` has `providerType = "zhipuai"`
- **THEN** the language adapter SHALL use the `/paas/v4/chat/completions` path
- **AND** the embedding adapter SHALL use the `/paas/v4/embeddings` path

#### Scenario: Ollama model discovery endpoint
- **WHEN** `discoverModels` is called on the Ollama provider type
- **THEN** it SHALL query the `/api/tags` endpoint instead of `/v1/models`
- **AND** it SHALL parse the `models` key from the response instead of `data`
- **AND** discovered model type inference SHALL remain low confidence unless remote metadata explicitly identifies model purpose

#### Scenario: SiliconFlow typed model discovery
- **WHEN** model discovery is called for a SiliconFlow provider
- **THEN** the provider type SHALL use official typed query parameters when they can identify supported runtime model types such as chat or embedding
- **AND** discovered models returned from a typed query SHALL be normalized with `source = remote` and `confidence = high`

#### Scenario: SiliconFlow embedding batch limit
- **WHEN** `maxEmbeddingsPerCall()` is called on the SiliconFlow provider type
- **THEN** it SHALL return 32

#### Scenario: Kimi capability discovery
- **WHEN** model discovery is called for a Kimi provider
- **THEN** the provider type SHALL parse remote model capability flags when present
- **AND** it SHALL map supported language features such as vision or reasoning only when those remote flags explicitly confirm them

### Requirement: Provider-specific typed model discovery
The system SHALL prefer official provider-specific model discovery when the provider API can confirm model type, model features, or typed model grouping without relying on a static model catalog.

#### Scenario: Remote typed discovery is normalized
- **WHEN** a built-in provider model-list API returns model type, feature fields, or typed endpoint/query context
- **THEN** the provider type SHALL normalize each supported discovered model into `DiscoveredModel`
- **AND** the normalized profile SHALL include `modelType`, `features`, `adapterType`, `source`, and `confidence`
- **AND** remote-confirmed model type metadata SHALL use `source = remote` and `confidence = high`

#### Scenario: Default fallback remains low confidence
- **WHEN** a provider has no verified typed model-list API
- **THEN** the provider type SHALL use the default OpenAI-compatible discovery behavior when applicable
- **AND** model type inference based only on model ID rules SHALL use `source = rule` and `confidence = low`

#### Scenario: OpenAI compatible custom provider fallback
- **WHEN** an `AiProvider` has `providerType = "openailike"`
- **THEN** model discovery SHALL continue to use the default OpenAI-compatible fallback
- **AND** the backend SHALL NOT attempt provider-specific typed discovery for that custom provider type

#### Scenario: No provider static catalog
- **WHEN** the backend normalizes discovered model profiles
- **THEN** it SHALL NOT classify models by a hardcoded provider-specific model ID catalog
- **AND** it SHALL NOT add models that were not returned by the provider discovery API

### Requirement: Provider discovery helper reuse
The system SHALL provide reusable provider-owned discovery helpers so provider-specific discovery can share common request, parsing, and profile construction behavior without introducing provider-name branches in endpoint code.

#### Scenario: Provider classes compose shared helpers
- **WHEN** a provider type implements provider-specific model discovery
- **THEN** it SHALL keep the provider-specific behavior in that provider type or provider-owned helper
- **AND** it SHOULD reuse shared helper methods for common OpenAI-compatible list requests and `DiscoveredModel` construction
- **AND** `ProviderConsoleEndpoint` SHALL continue to call only `providerType.discoverModels(provider, apiKey)`

### Requirement: Provider type endpoint recommendation
The system SHALL use provider type behavior to recommend internal adapter types for discovered models and for model creation requests that omit `spec.adapterType`.

#### Scenario: Discovery returns recommended adapter type
- **WHEN** a client calls the provider model discovery endpoint for an `AiProvider`
- **THEN** each discovered model item SHALL include a recommended adapter type when the provider type can map the candidate model profile to a supported adapter type
- **AND** the recommended adapter type SHALL be one of the provider type's supported adapter types

#### Scenario: Embedding model maps to embedding adapter
- **WHEN** a discovered model has `modelType = embedding`
- **AND** the provider type supports an embedding adapter type
- **THEN** the backend SHALL recommend the provider type's supported embedding adapter type

#### Scenario: Language model maps to language adapter
- **WHEN** a discovered model has `modelType = language`
- **AND** the provider type supports a language adapter type
- **THEN** the backend SHALL recommend the provider type's supported language adapter type

#### Scenario: Model creation defaults missing adapter type
- **WHEN** a client creates an `AiModel` without `spec.adapterType`
- **THEN** the backend SHALL resolve the referenced `AiProvider` by `spec.providerName`
- **AND** apply a provider type adapter recommendation for the model before validation
- **AND** persist only an adapter type supported by the provider type

#### Scenario: Unsupported adapter recommendation fails validation
- **WHEN** the provider type cannot recommend a supported adapter type for a model
- **THEN** the backend SHALL reject model creation with a validation error
- **AND** the backend SHALL NOT persist an `AiModel` with an unsupported or blank adapter type

### Requirement: Frontend consumes provider type API
The frontend SHALL fetch provider type metadata from the REST API and render provider selection, model type choices, feature choices, and adapter behavior dynamically, eliminating hardcoded provider type and adapter constant lists.

#### Scenario: Provider type dropdown rendered from API
- **WHEN** the user opens the provider creation form
- **THEN** the frontend SHALL fetch `GET /provider-types`
- **AND** the provider type dropdown SHALL be populated from the API response `displayName` and `providerType` fields
- **AND** the frontend SHALL NOT use any hardcoded `SUPPORTED_PROVIDER_TYPES` or `PROVIDER_TYPE_LABELS` constant

#### Scenario: Base URL field visibility driven by API
- **WHEN** the user selects a provider type in the creation form
- **THEN** the base URL field SHALL be shown if the selected type's `requiresBaseUrl` is true
- **AND** the base URL placeholder SHALL display the `defaultBaseUrl` value
- **AND** the frontend SHALL NOT use any hardcoded `requiresBaseUrl` logic

#### Scenario: Adapter inference driven by API
- **WHEN** the user discovers models for a provider
- **THEN** the adapter type SHALL be inferred from the provider type metadata and backend discovery result
- **AND** the frontend SHALL NOT use any hardcoded provider-type-to-adapter-type mapping

#### Scenario: Provider list display driven by API
- **WHEN** the provider list page renders a provider card
- **THEN** the display name for the provider type SHALL come from the API metadata
- **AND** the frontend SHALL NOT use any hardcoded `PROVIDER_TYPE_LABELS` constant
