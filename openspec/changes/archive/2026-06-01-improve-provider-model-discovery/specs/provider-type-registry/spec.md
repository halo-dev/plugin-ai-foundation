## ADDED Requirements

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

## MODIFIED Requirements

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
