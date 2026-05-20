## Purpose

Define MiniMax provider registration, metadata, model construction behavior, and icon assets.

## Requirements

### Requirement: MiniMax is registered as a built-in AI provider type
The system SHALL expose MiniMax as a built-in AI provider type via the provider type discovery mechanism.

#### Scenario: Provider type discovery includes MiniMax
- **WHEN** the system queries all available provider types via `ApplicationContext.getBeansOfType(AiProviderType.class)`
- **THEN** a provider with `providerType = "minimax"` is present in the result

#### Scenario: Provider type metadata is correct
- **WHEN** the system retrieves metadata for the MiniMax provider type
- **THEN** `getDisplayName()` returns "MiniMax"
- **AND** `getProviderType()` returns "minimax"
- **AND** `isBuiltIn()` returns `true`
- **AND** `requiresBaseUrl()` returns `false`

### Requirement: MiniMax provider exposes complete metadata
The system SHALL expose MiniMax provider metadata through the provider-types REST API.

#### Scenario: Metadata endpoint returns MiniMax details
- **WHEN** a client calls `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** the response includes MiniMax with the following fields:
  - `displayName`: "MiniMax"
  - `description`: a description of MiniMax's capabilities
  - `iconUrl`: `/plugins/ai-foundation/assets/static/brands/minimax.png`
  - `websiteUrl`: `https://www.minimaxi.com`
  - `documentationUrl`: `https://platform.minimaxi.com/docs/api-reference/api-overview.md`
  - `defaultBaseUrl`: `https://api.minimax.io`
  - `supportedEndpointTypes`: `["openai-chat"]`

### Requirement: MiniMax chat model can be constructed
The system SHALL construct a functional Spring AI `ChatModel` for MiniMax given a valid `AiProvider` configuration and API key.

#### Scenario: Chat model construction succeeds
- **WHEN** `buildChatModel(AiProvider, apiKey, modelId)` is called with valid parameters
- **THEN** the method returns a non-null `ChatModel` instance
- **AND** the model is configured with the provider's base URL (or default if not overridden)
- **AND** the model uses the OpenAI chat completions endpoint (`/v1/chat/completions`)

#### Scenario: Custom base URL is respected
- **WHEN** the `AiProvider` specifies a custom `baseUrl` (e.g., `https://api.minimaxi.com`)
- **THEN** the constructed `ChatModel` uses the custom base URL instead of the default

### Requirement: MiniMax provider does not claim embedding support
The system SHALL correctly indicate that MiniMax does not support embeddings through the standard API.

#### Scenario: Embedding support flags are false
- **WHEN** the system queries embedding support for MiniMax
- **THEN** `supportsEmbeddings()` returns `false`
- **AND** `maxEmbeddingsPerCall()` returns `0`
- **AND** `supportsParallelCalls()` returns `false`

### Requirement: MiniMax provider icon asset is available
The system SHALL provide a MiniMax brand icon at the expected static asset path.

#### Scenario: Icon asset is accessible
- **WHEN** a client requests `/plugins/ai-foundation/assets/static/brands/minimax.png`
- **THEN** the server returns the MiniMax brand icon image
