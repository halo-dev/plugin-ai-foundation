## MODIFIED Requirements

### Requirement: Test chat against configured model
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
