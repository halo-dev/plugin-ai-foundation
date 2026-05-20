## MODIFIED Requirements

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
- **AND** supported model types SHALL include `language`
- **AND** supported adapter types SHALL include `ollama-chat`

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

#### Scenario: SiliconFlow embedding batch limit
- **WHEN** `maxEmbeddingsPerCall()` is called on the SiliconFlow provider type
- **THEN** it SHALL return 32

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
