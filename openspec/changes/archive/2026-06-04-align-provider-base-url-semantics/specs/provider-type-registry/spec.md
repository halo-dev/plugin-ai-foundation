## MODIFIED Requirements

### Requirement: AbstractAiProviderType base class
The system SHALL provide an `AbstractAiProviderType` base class that supplies default implementations for common behavior.

#### Scenario: Default model discovery
- **WHEN** a provider type extends `AbstractAiProviderType` and does not override `discoverModels`
- **THEN** the base class SHALL provide a default implementation that queries the provider's `/models` endpoint relative to the resolved provider base URL
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

### Requirement: Concrete provider type implementations
The system SHALL provide `AiProviderType` implementations for all currently supported provider types: `openai`, `aihubmix`, `deepseek`, `siliconflow`, `doubao`, `ernie`, `zhipuai`, `ollama`, `openailike`, `minimax`, `kimi`, `openrouter`, `dashscope`, `gitee-moark`, and `mimo`.

#### Scenario: OpenAI provider type metadata
- **WHEN** the OpenAI provider type is queried
- **THEN** `getProviderType()` SHALL return "openai"
- **AND** `getDisplayName()` SHALL return "OpenAI"
- **AND** `isBuiltIn()` SHALL return true
- **AND** `requiresBaseUrl()` SHALL return false
- **AND** `getDefaultBaseUrl()` SHALL return "https://api.openai.com/v1"
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

#### Scenario: OpenAI-compatible resource paths
- **WHEN** a provider type uses the Spring AI OpenAI-compatible client for standard chat, embedding, or default model discovery endpoints
- **THEN** the provider type SHALL treat the resolved base URL as the provider-documented API base URL
- **AND** the chat completions path SHALL be `/chat/completions`
- **AND** the embeddings path SHALL be `/embeddings`
- **AND** the default model discovery path SHALL be `/models`
- **AND** the provider type SHALL NOT expose those endpoint paths as user-editable provider configuration

#### Scenario: Provider type exposes chat completions path for URL preview
- **WHEN** a client calls `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** each provider type with a known language model endpoint SHALL include read-only `completionsPath` metadata
- **AND** OpenAI-compatible provider types SHALL report `/chat/completions`
- **AND** the Ollama provider type SHALL report `/api/chat`
- **AND** clients SHALL treat `completionsPath` as preview metadata rather than user-editable provider configuration

#### Scenario: AiHubMix provider-specific behavior
- **WHEN** an `AiProvider` has `providerType = "aihubmix"`
- **THEN** the runtime SHALL use the built-in AiHubMix default baseUrl
- **AND** the runtime SHALL apply the provider-specific request header behavior required by AiHubMix

#### Scenario: AiHubMix typed model discovery
- **WHEN** model discovery is called for an AiHubMix provider
- **THEN** the provider type SHALL use AiHubMix model metadata fields such as model type and feature fields when available
- **AND** it SHALL map supported language and embedding profiles to AI Foundation model types and adapter types without using a static model catalog
- **AND** it MAY use a provider-specific model catalog endpoint when that endpoint is not under the provider's OpenAI-compatible API base URL

#### Scenario: DouBao API base URL prefix
- **WHEN** an `AiProvider` has `providerType = "doubao"`
- **THEN** the runtime SHALL use a provider-documented API base URL that includes DouBao's API version prefix
- **AND** the language adapter SHALL use the `/chat/completions` path
- **AND** the embedding adapter SHALL use the `/embeddings` path

#### Scenario: Ernie API base URL prefix
- **WHEN** an `AiProvider` has `providerType = "ernie"`
- **THEN** the runtime SHALL use a provider-documented API base URL that includes Ernie's API version prefix
- **AND** the language adapter SHALL use the `/chat/completions` path
- **AND** the embedding adapter SHALL use the `/embeddings` path

#### Scenario: ZhiPu API base URL prefix
- **WHEN** an `AiProvider` has `providerType = "zhipuai"`
- **THEN** the runtime SHALL use a provider-documented API base URL that includes ZhiPu's platform and API version prefix
- **AND** the language adapter SHALL use the `/chat/completions` path
- **AND** the embedding adapter SHALL use the `/embeddings` path

#### Scenario: Ollama model discovery endpoint
- **WHEN** `discoverModels` is called on the Ollama provider type
- **THEN** it SHALL query the `/api/tags` endpoint instead of `/models`
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
