## MODIFIED Requirements

### Requirement: Provider debug endpoints
The backend SHALL expose admin/debug endpoints for model discovery, connectivity validation, and test chat without requiring the Console UI.

#### Scenario: List provider models
- **WHEN** an admin calls `GET /providers/{name}/models`
- **THEN** the system SHALL resolve the provider's `ProviderAdapter` via `ProviderAdapterFactory.create()`
- **AND** call `adapter.discoverModels()` to fetch the model list
- **AND** each model in the response SHALL include `modelId`, `displayName`, and `capabilities` fields
- **AND** if the remote API call fails, the system SHALL fall back to returning locally stored `AiModel` resources for that provider

#### Scenario: Discovery response format
- **WHEN** the model discovery endpoint returns successfully
- **THEN** the response body SHALL contain `{ "models": [...], "providerName": "..." }`
- **AND** each model object SHALL include `"modelId"`, `"displayName"`, and `"capabilities"` (an array of capability strings, e.g., `["chat"]` or `["embedding"]`)

#### Scenario: Test provider connectivity
- **WHEN** an admin calls `POST /providers/{name}/connectivity`
- **THEN** the system SHALL validate the provider configuration and update `status.phase`, `status.message`, and `status.lastCheckedAt`

#### Scenario: Test chat against configured model
- **WHEN** an admin calls `POST /models/{name}/test-chat` where `{name}` is `AiModel.metadata.name`
- **AND** the request body contains a `prompt`
- **THEN** the system SHALL resolve the configured model by `metadata.name` via `client.fetch`
- **AND** the system SHALL execute a non-streaming chat request using the configured provider
- **AND** the response SHALL include `modelName`, generated `content`, and available completion metadata such as `finishReason` and `usage`

#### Scenario: Minimal test chat request and response
- **WHEN** a client calls the `test-chat` endpoint in the first phase
- **THEN** the request body SHALL only need to contain `prompt`
- **AND** the response SHALL include `modelName`, `content`, `finishReason`, and `usage`
- **AND** the first phase SHALL NOT require the full public `ChatResponse` shape for this debug endpoint
