## ADDED Requirements

### Requirement: Provider debug endpoints
The backend SHALL expose admin/debug endpoints for model discovery, connectivity validation, and test chat without requiring the Console UI.

#### Scenario: List provider models
- **WHEN** an admin calls `GET /providers/{name}/models`
- **THEN** the system SHALL fetch or return the discovered model list for the specified provider

#### Scenario: Test provider connectivity
- **WHEN** an admin calls `POST /providers/{name}/connectivity`
- **THEN** the system SHALL validate the provider configuration and update `status.phase`, `status.message`, and `status.lastCheckedAt`

#### Scenario: Test chat against configured model
- **WHEN** an admin calls `POST /providers/{providerName}/models/{modelId}/test-chat`
- **AND** the request body contains a `prompt`
- **THEN** the system SHALL resolve the configured model by `providerName/modelId`
- **AND** the system SHALL execute a non-streaming chat request using the configured provider
- **AND** the response SHALL include `modelRef`, generated `content`, and available completion metadata such as `finishReason` and `usage`

#### Scenario: Minimal test chat request and response
- **WHEN** a client calls the `test-chat` endpoint in the first phase
- **THEN** the request body SHALL only need to contain `prompt`
- **AND** the response SHALL include `modelRef`, `content`, `finishReason`, and `usage`
- **AND** the first phase SHALL NOT require the full public `ChatResponse` shape for this debug endpoint
