## ADDED Requirements

### Requirement: Xiaomi MiMo is registered as a built-in AI provider type
The system SHALL expose Xiaomi MiMo as a built-in AI provider type via the provider type discovery mechanism.

#### Scenario: Provider type discovery includes Xiaomi MiMo
- **WHEN** the system queries all available provider types via `ApplicationContext.getBeansOfType(AiProviderType.class)`
- **THEN** a provider with `providerType = "mimo"` is present in the result

#### Scenario: Provider type metadata is correct
- **WHEN** the system retrieves metadata for the Xiaomi MiMo provider type
- **THEN** `getDisplayName()` returns `"Xiaomi MiMo"`
- **AND** `getProviderType()` returns `"mimo"`
- **AND** `isBuiltIn()` returns `true`
- **AND** `requiresBaseUrl()` returns `false`
- **AND** `getDefaultBaseUrl()` returns `"https://api.xiaomimimo.com"`

### Requirement: Xiaomi MiMo provider exposes complete metadata
The system SHALL expose Xiaomi MiMo provider metadata through the provider-types REST API.

#### Scenario: Metadata endpoint returns Xiaomi MiMo details
- **WHEN** a client calls `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** the response includes Xiaomi MiMo with the following fields:
  - `providerType`: `"mimo"`
  - `displayName`: `"Xiaomi MiMo"`
  - `description`: a non-empty description of Xiaomi MiMo's capabilities
  - `iconUrl`: `"/plugins/ai-foundation/assets/static/brands/xiaomimimo.png"`
  - `websiteUrl`: `"https://platform.xiaomimimo.com/"`
  - `documentationUrl`: `"https://platform.xiaomimimo.com/#/docs/welcome"`
  - `defaultBaseUrl`: `"https://api.xiaomimimo.com"`
  - `supportedAdapterTypes`: `["openai-chat"]`

### Requirement: Xiaomi MiMo chat model can be constructed
The system SHALL construct a functional Spring AI `ChatModel` for Xiaomi MiMo given a valid `AiProvider` configuration, API key, and model ID.

#### Scenario: Chat model construction succeeds
- **WHEN** `buildChatModel(AiProvider, apiKey, modelId)` is called with valid parameters
- **THEN** the method returns a non-null `ChatModel` instance
- **AND** the model is configured with the provider's base URL, or `https://api.xiaomimimo.com` when not overridden
- **AND** the model uses the OpenAI chat completions endpoint `/v1/chat/completions`

#### Scenario: Custom base URL is respected
- **WHEN** the `AiProvider` specifies a custom `baseUrl`
- **THEN** the constructed `ChatModel` uses the custom base URL instead of `https://api.xiaomimimo.com`

### Requirement: Xiaomi MiMo supports model discovery through the OpenAI-compatible models endpoint
The system SHALL use the default OpenAI-compatible model discovery flow for Xiaomi MiMo.

#### Scenario: Discover Xiaomi MiMo models
- **WHEN** `discoverModels(provider, apiKey)` is called on the Xiaomi MiMo provider type
- **THEN** the system sends `GET {baseUrl}/v1/models`
- **AND** includes the API key as a bearer token when present
- **AND** parses each returned `data[].id` as a discovered model ID
- **AND** recommends `openai-chat` for discovered language models

### Requirement: Xiaomi MiMo provider does not claim embedding support
The system SHALL correctly indicate that Xiaomi MiMo does not support embeddings through the current provider integration.

#### Scenario: Embedding support flags are false
- **WHEN** the system queries embedding support for Xiaomi MiMo
- **THEN** `getSupportedAdapterTypes()` returns only `["openai-chat"]`
- **AND** `buildEmbeddingModel(provider, apiKey, modelId)` returns `null`
- **AND** `maxEmbeddingsPerCall()` returns `0`
- **AND** `supportsParallelCalls()` returns `false`

### Requirement: Xiaomi MiMo provider icon asset is available
The system SHALL provide a Xiaomi MiMo brand icon at the expected static asset path.

#### Scenario: Icon asset is accessible
- **WHEN** a client requests `/plugins/ai-foundation/assets/static/brands/xiaomimimo.png`
- **THEN** the server returns the Xiaomi MiMo brand icon image
