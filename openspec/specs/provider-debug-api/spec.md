### Requirement: Provider type metadata is served by a dedicated endpoint
The system SHALL expose provider type metadata via a dedicated `ProviderTypeConsoleEndpoint` separate from provider CRUD operations.

#### Scenario: Provider types endpoint is independent
- **WHEN** a client requests provider type metadata
- **THEN** the request is handled by `ProviderTypeConsoleEndpoint` at `provider-types`
- **AND** it is not mixed with `AiProvider` CRUD routes in `ProviderConsoleEndpoint`

### Requirement: Provider remote model discovery
The backend SHALL expose a remote model discovery endpoint via `ProviderConsoleEndpoint` for fetching models from a provider's remote API.

#### Scenario: Discover provider models
- **WHEN** an admin calls `GET providers/{name}/discover-models`
- **THEN** the system SHALL resolve the provider's adapter via the provider type
- **AND** call the adapter's model discovery method to fetch the model list
- **AND** each model in the response SHALL include `modelId`, `displayName`, and `capabilities` fields
- **AND** if the remote API call fails, the system SHALL return an error response without falling back to locally stored `AiModel` resources

#### Scenario: Discovery response format
- **WHEN** the model discovery endpoint returns successfully
- **THEN** the response body SHALL contain `{ "models": [...], "providerName": "..." }`
- **AND** each model object SHALL include `"modelId"`, `"displayName"`, and `"capabilities"` (an array of capability strings, e.g., `["chat"]` or `["embedding"]`)

### Requirement: Test provider connectivity
The backend SHALL expose a connectivity validation endpoint via `ProviderConsoleEndpoint` that performs an actual remote API call to verify the provider is reachable.

#### Scenario: Test provider connectivity with valid configuration
- **WHEN** an admin calls `POST providers/{name}/connectivity`
- **THEN** the system SHALL resolve the provider's adapter via the provider type
- **AND** call the adapter's `discoverModels()` method to send a real HTTP request to the provider's remote API
- **AND** if the remote API call succeeds, the system SHALL set `status.phase` to `OK`, `status.message` to a success message, and `status.lastCheckedAt` to the current timestamp
- **AND** return the updated status in the response

#### Scenario: Test provider connectivity with invalid configuration
- **WHEN** an admin calls `POST providers/{name}/connectivity`
- **AND** the provider's remote API is unreachable, returns an authentication error, or the base URL is invalid
- **THEN** the system SHALL set `status.phase` to `ERROR`, `status.message` to the error message from the failed remote call, and `status.lastCheckedAt` to the current timestamp
- **AND** return the updated status in the response

### Requirement: Test chat against configured model
The backend SHALL expose a test chat endpoint via `ModelConsoleEndpoint` for executing a non-streaming chat request against a configured model.

#### Scenario: Test chat against configured model
- **WHEN** an admin calls `POST models/{name}/test-chat` where `{name}` is `AiModel.metadata.name`
- **AND** the request body contains a `prompt`
- **THEN** the system SHALL resolve the configured model by `metadata.name`
- **AND** the system SHALL execute a non-streaming chat request using the configured provider
- **AND** the response SHALL include `modelName`, generated `content`, and available completion metadata such as `finishReason` and `usage`

#### Scenario: Minimal test chat request and response
- **WHEN** a client calls the `test-chat` endpoint
- **THEN** the request body SHALL only need to contain `prompt`
- **AND** the response SHALL include `modelName`, `content`, `finishReason`, and `usage`

### Requirement: Endpoint routes use relative paths
All endpoints registered via Halo `CustomEndpoint` SHALL use relative path strings without a leading `/`.

#### Scenario: Endpoint route registration
- **WHEN** inspecting any `CustomEndpoint` implementation's route definitions
- **THEN** no route string starts with `/`
