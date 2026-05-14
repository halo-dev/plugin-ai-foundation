## ADDED Requirements

### Requirement: Provider type metadata is served by a dedicated endpoint
The system SHALL expose provider type metadata via a dedicated `ProviderTypeConsoleEndpoint` separate from provider CRUD operations.

#### Scenario: Provider types endpoint is independent
- **WHEN** a client requests provider type metadata
- **THEN** the request is handled by `ProviderTypeConsoleEndpoint` at `provider-types`
- **AND** it is not mixed with `AiProvider` CRUD routes in `ProviderConsoleEndpoint`

### Requirement: Provider remote model discovery has no local fallback
The system SHALL expose provider remote model discovery at `providers/{name}/discover-models` and SHALL NOT fall back to locally configured `AiModel` resources when remote discovery returns empty or fails.

#### Scenario: Remote discovery returns empty
- **WHEN** a client calls `GET providers/{name}/discover-models`
- **AND** the provider's remote API returns no models
- **THEN** the response contains an empty models array
- **AND** the system does not query local `AiModel` extensions as fallback

#### Scenario: Remote discovery fails
- **WHEN** a client calls `GET providers/{name}/discover-models`
- **AND** the provider's remote API throws an error
- **THEN** the system returns an error response
- **AND** the system does not query local `AiModel` extensions as fallback

### Requirement: Endpoint routes use relative paths
All endpoints registered via Halo `CustomEndpoint` SHALL use relative path strings without a leading `/`.

#### Scenario: Endpoint route registration
- **WHEN** inspecting any `CustomEndpoint` implementation's route definitions
- **THEN** no route string starts with `/`
