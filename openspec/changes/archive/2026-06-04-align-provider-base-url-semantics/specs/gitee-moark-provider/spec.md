## MODIFIED Requirements

### Requirement: Gitee 模力方舟 is registered as a built-in AI provider type
The system SHALL expose Gitee 模力方舟 as a built-in AI provider type via the provider type discovery mechanism.

#### Scenario: Provider type discovery includes Gitee 模力方舟
- **WHEN** the system queries all available provider types via `ApplicationContext.getBeansOfType(AiProviderType.class)`
- **THEN** a provider with `providerType = "gitee-moark"` is present in the result

#### Scenario: Provider type metadata is correct
- **WHEN** the system retrieves metadata for the Gitee 模力方舟 provider type
- **THEN** `getDisplayName()` returns `"Gitee 模力方舟"`
- **AND** `getProviderType()` returns `"gitee-moark"`
- **AND** `isBuiltIn()` returns `true`
- **AND** `requiresBaseUrl()` returns `false`
- **AND** `getDefaultBaseUrl()` returns `"https://ai.gitee.com/v1"`

### Requirement: Gitee 模力方舟 provider exposes complete metadata
The system SHALL expose Gitee 模力方舟 provider metadata through the provider-types REST API.

#### Scenario: Metadata endpoint returns Gitee 模力方舟 details
- **WHEN** a client calls `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** the response includes Gitee 模力方舟 with the following fields:
  - `providerType`: `"gitee-moark"`
  - `displayName`: `"Gitee 模力方舟"`
  - `description`: a non-empty description of Gitee 模力方舟's capabilities
  - `iconUrl`: `"/plugins/ai-foundation/assets/static/brands/gitee-moark.png"`
  - `websiteUrl`: `"https://ai.gitee.com/"`
  - `documentationUrl`: `"https://ai.gitee.com/docs/products/apis/texts/text-generation"`
  - `defaultBaseUrl`: `"https://ai.gitee.com/v1"`
  - `supportedAdapterTypes`: `["openai-chat"]`

### Requirement: Gitee 模力方舟 chat model can be constructed
The system SHALL construct a functional Spring AI `ChatModel` for Gitee 模力方舟 given a valid `AiProvider` configuration, API key, and model ID.

#### Scenario: Chat model construction succeeds
- **WHEN** `buildChatModel(AiProvider, apiKey, modelId)` is called with valid parameters
- **THEN** the method returns a non-null `ChatModel` instance
- **AND** the model is configured with the provider's base URL, or `https://ai.gitee.com/v1` when not overridden
- **AND** the model uses the OpenAI chat completions endpoint `/chat/completions`
- **AND** the model sends the API key as a bearer token through the OpenAI-compatible client

#### Scenario: Custom base URL is respected
- **WHEN** the `AiProvider` specifies a custom `baseUrl`
- **THEN** the constructed `ChatModel` uses the custom base URL instead of `https://ai.gitee.com/v1`

### Requirement: Gitee 模力方舟 supports model discovery through the OpenAI-compatible models endpoint
The system SHALL use the default OpenAI-compatible model discovery flow for Gitee 模力方舟.

#### Scenario: Discover Gitee 模力方舟 models
- **WHEN** `discoverModels(provider, apiKey)` is called on the Gitee 模力方舟 provider type
- **THEN** the system sends `GET {baseUrl}/models`
- **AND** includes the API key as a bearer token when present
- **AND** parses each returned `data[].id` as a discovered model ID
- **AND** recommends `openai-chat` for discovered language models
