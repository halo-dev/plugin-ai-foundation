## MODIFIED Requirements

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
