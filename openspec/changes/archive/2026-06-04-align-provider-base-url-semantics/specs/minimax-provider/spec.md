## MODIFIED Requirements

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
  - `defaultBaseUrl`: `https://api.minimaxi.com/v1`
  - `supportedEndpointTypes`: `["openai-chat"]`

### Requirement: MiniMax chat model can be constructed
The system SHALL construct a functional Spring AI `ChatModel` for MiniMax given a valid `AiProvider` configuration and API key.

#### Scenario: Chat model construction succeeds
- **WHEN** `buildChatModel(AiProvider, apiKey, modelId)` is called with valid parameters
- **THEN** the method returns a non-null `ChatModel` instance
- **AND** the model is configured with the provider's base URL (or default if not overridden)
- **AND** the model uses the OpenAI chat completions endpoint (`/chat/completions`)

#### Scenario: Custom base URL is respected
- **WHEN** the `AiProvider` specifies a custom `baseUrl` (e.g., `https://api.minimaxi.com/v1`)
- **THEN** the constructed `ChatModel` uses the custom base URL instead of the default
