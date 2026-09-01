# xiaomi-mimo-provider Specification

## Purpose
Define the built-in Xiaomi MiMo provider integration.

## Requirements

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
- **AND** `getDefaultBaseUrl()` returns `"https://api.xiaomimimo.com/v1"`

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
  - `defaultBaseUrl`: `"https://api.xiaomimimo.com/v1"`
  - `supportedAdapterTypes`: `["openai-chat"]`

### Requirement: Xiaomi MiMo chat model can be constructed
The system SHALL construct a functional Spring AI `ChatModel` for Xiaomi MiMo given a valid `AiProvider` configuration, API key, and model ID.

#### Scenario: Chat model construction succeeds
- **WHEN** `buildChatModel(AiProvider, apiKey, modelId)` is called with valid parameters
- **THEN** the method returns a non-null `ChatModel` instance
- **AND** the model is configured with the provider's base URL, or `https://api.xiaomimimo.com/v1` when not overridden
- **AND** the model uses the OpenAI chat completions endpoint `/chat/completions`

#### Scenario: Custom base URL is respected
- **WHEN** the `AiProvider` specifies a custom `baseUrl`
- **THEN** the constructed `ChatModel` uses the custom base URL instead of `https://api.xiaomimimo.com/v1`

### Requirement: Xiaomi MiMo supports model discovery through the OpenAI-compatible models endpoint
The system SHALL use the default OpenAI-compatible model discovery flow for Xiaomi MiMo.

#### Scenario: Discover Xiaomi MiMo models
- **WHEN** `discoverModels(provider, apiKey)` is called on the Xiaomi MiMo provider type
- **THEN** the system sends `GET {baseUrl}/models`
- **AND** includes the API key as a bearer token when present
- **AND** parses each returned `data[].id` as a discovered model ID
- **AND** recommends `openai-chat` for discovered language models

### Requirement: MiMo provider-owned Chat and Responses semantics
The MiMo provider SHALL expose distinct provider-owned Chat Completions and Responses adapters using the documented MiMo endpoint and event schemas.

#### Scenario: Invoke MiMo Responses
- **WHEN** a MiMo model selects the Responses adapter
- **THEN** the runtime SHALL send a MiMo Responses request
- **AND** SHALL normalize MiMo text, reasoning, function-call, annotation, and usage events

#### Scenario: Invoke MiMo Chat
- **WHEN** a MiMo model selects the Chat adapter
- **THEN** the runtime SHALL use MiMo's documented Chat Completions schema and constraints

### Requirement: MiMo reasoning and tool constraints
The MiMo adapter SHALL enforce documented reasoning, sampling, tool choice, and continuation constraints.

#### Scenario: Thinking mode tool continuation
- **WHEN** a MiMo thinking response contains reasoning and tool calls
- **THEN** the continuation SHALL preserve required reasoning content
- **AND** non-`auto` tool-choice values SHALL be omitted to match MiMo's documented normalization

#### Scenario: Thinking mode sampling normalization
- **WHEN** a MiMo request enables thinking and contains `temperature` or `top_p`
- **THEN** the adapter SHALL omit those fields before invocation because MiMo applies fixed sampling defaults in thinking mode
- **AND** SHALL preserve explicitly configured sampling fields when thinking is disabled

### Requirement: MiMo provider metadata
The MiMo adapter SHALL retain documented cache, web-search, reasoning-token, annotation, and provider usage details as provider-neutral fields or provider metadata.

#### Scenario: Web search annotations returned
- **WHEN** MiMo returns web-search annotations and usage
- **THEN** normalized output SHALL expose supported sources
- **AND** additional search usage SHALL be retained as provider metadata
