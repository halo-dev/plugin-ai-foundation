## Requirements

### Requirement: AiProviderType interface definition
The system SHALL define a `AiProviderType` interface that encapsulates identity, display metadata, configuration metadata, and behavior for an AI provider type.

#### Scenario: Interface contract
- **WHEN** a class implements `AiProviderType`
- **THEN** it MUST provide `getProviderType()` returning a unique string identifier
- **AND** it MUST provide `getDisplayName()` returning a human-readable name
- **AND** it MUST provide `isBuiltIn()` indicating whether the provider has built-in defaults
- **AND** it MUST provide `requiresBaseUrl()` indicating whether the user must supply a base URL
- **AND** it MUST provide `getDefaultBaseUrl()` returning the default base URL (may be null if requiresBaseUrl is true and no default exists)
- **AND** it MUST provide `getSupportedEndpointTypes()` returning a list of supported endpoint type strings
- **AND** it MUST provide `supportsEmbeddings()` indicating whether the provider supports embedding models

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

### Requirement: AbstractAiProviderType base class
The system SHALL provide an `AbstractAiProviderType` base class that supplies default implementations for common behavior.

#### Scenario: Default model discovery
- **WHEN** a provider type extends `AbstractAiProviderType` and does not override `discoverModels`
- **THEN** the base class SHALL provide a default implementation that queries the provider's `/v1/models` endpoint

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
- **AND** each object SHALL include `providerType`, `displayName`, `description`, `iconUrl`, `documentationUrl`, `websiteUrl`, `builtIn`, `requiresBaseUrl`, `defaultBaseUrl`, `supportedEndpointTypes`, and `supportsEmbeddings`

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
- **AND** `getSupportedEndpointTypes()` SHALL return ["openai-chat", "openai-embedding"]
- **AND** `supportsEmbeddings()` SHALL return true

#### Scenario: Ollama provider type metadata
- **WHEN** the Ollama provider type is queried
- **THEN** `getProviderType()` SHALL return "ollama"
- **AND** `getDisplayName()` SHALL return "Ollama"
- **AND** `isBuiltIn()` SHALL return false
- **AND** `requiresBaseUrl()` SHALL return true
- **AND** `getDefaultBaseUrl()` SHALL return "http://localhost:11434"
- **AND** `getSupportedEndpointTypes()` SHALL return ["ollama-chat"]
- **AND** `supportsEmbeddings()` SHALL return true

#### Scenario: OpenAI-Like provider type metadata
- **WHEN** the OpenAI-Like provider type is queried
- **THEN** `getProviderType()` SHALL return "openailike"
- **AND** `getDisplayName()` SHALL return "OpenAI Compatible"
- **AND** `isBuiltIn()` SHALL return false
- **AND** `requiresBaseUrl()` SHALL return true
- **AND** `getDefaultBaseUrl()` SHALL return null
- **AND** `getSupportedEndpointTypes()` SHALL return ["openai-chat", "openai-embedding"]
- **AND** `supportsEmbeddings()` SHALL return true

#### Scenario: DeepSeek provider type metadata
- **WHEN** the DeepSeek provider type is queried
- **THEN** `getProviderType()` SHALL return "deepseek"
- **AND** `getDisplayName()` SHALL return "DeepSeek"
- **AND** `isBuiltIn()` SHALL return true
- **AND** `requiresBaseUrl()` SHALL return false
- **AND** `getDefaultBaseUrl()` SHALL return "https://api.deepseek.com"
- **AND** `getSupportedEndpointTypes()` SHALL return ["openai-chat"]
- **AND** `supportsEmbeddings()` SHALL return false

#### Scenario: AiHubMix provider-specific behavior
- **WHEN** an `AiProvider` has `providerType = "aihubmix"`
- **THEN** the runtime SHALL use the built-in AiHubMix default baseUrl
- **AND** the runtime SHALL apply the provider-specific request header behavior required by AiHubMix

#### Scenario: DouBao non-standard API paths
- **WHEN** an `AiProvider` has `providerType = "doubao"`
- **THEN** the chat model SHALL use the `/v3/chat/completions` path
- **AND** the embedding model SHALL use the `/v3/embeddings` path

#### Scenario: Ernie non-standard API paths
- **WHEN** an `AiProvider` has `providerType = "ernie"`
- **THEN** the chat model SHALL use the `/v2/chat/completions` path
- **AND** the embedding model SHALL use the `/v2/embeddings` path

#### Scenario: ZhiPu non-standard API paths
- **WHEN** an `AiProvider` has `providerType = "zhipuai"`
- **THEN** the chat model SHALL use the `/paas/v4/chat/completions` path
- **AND** the embedding model SHALL use the `/paas/v4/embeddings` path

#### Scenario: Ollama model discovery endpoint
- **WHEN** `discoverModels` is called on the Ollama provider type
- **THEN** it SHALL query the `/api/tags` endpoint instead of `/v1/models`
- **AND** it SHALL parse the `models` key from the response instead of `data`

#### Scenario: SiliconFlow embedding batch limit
- **WHEN** `maxEmbeddingsPerCall()` is called on the SiliconFlow provider type
- **THEN** it SHALL return 32

### Requirement: Frontend consumes provider type API
The frontend SHALL fetch provider type metadata from the REST API and render provider selection dynamically, eliminating all hardcoded provider type constant lists.

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

#### Scenario: Endpoint type inference driven by API
- **WHEN** the user discovers models for a provider
- **THEN** the endpoint type SHALL be inferred from the provider type's `supportedEndpointTypes` metadata
- **AND** the frontend SHALL NOT use any hardcoded provider-type-to-endpoint-type mapping

#### Scenario: Provider list display driven by API
- **WHEN** the provider list page renders a provider card
- **THEN** the display name for the provider type SHALL come from the API metadata
- **AND** the frontend SHALL NOT use any hardcoded `PROVIDER_TYPE_LABELS` constant
