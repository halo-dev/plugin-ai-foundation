## MODIFIED Requirements

### Requirement: Test provider connectivity
The backend SHALL expose a connectivity validation endpoint via `ProviderConsoleEndpoint` that performs an actual remote API call and returns a structured health report while updating the persisted provider status.

#### Scenario: Test provider connectivity with valid configuration
- **WHEN** an admin calls `POST providers/{name}/connectivity`
- **THEN** the system SHALL resolve the provider's adapter via the provider type
- **AND** call the adapter's `discoverModels()` method to send a real HTTP request to the provider's remote API
- **AND** the response SHALL contain the structured health report defined by the `provider-health-diagnostics` capability
- **AND** the system SHALL set `status.phase` to `OK`, `status.message` to a success summary, and `status.lastCheckedAt` to the current timestamp
- **AND** the response SHALL reflect the persisted status

#### Scenario: Test provider connectivity with invalid configuration
- **WHEN** an admin calls `POST providers/{name}/connectivity`
- **AND** the provider configuration is invalid, the remote API is unreachable, the provider returns an authentication error, or the endpoint is unsupported
- **THEN** the system SHALL set `status.phase` to `ERROR` and classify the failure in the structured report
- **AND** `status.message` SHALL contain a sanitized and bounded failure summary
- **AND** `status.lastCheckedAt` SHALL be the current timestamp
- **AND** the endpoint SHALL return HTTP 200 with the structured report rather than an unhandled server error

#### Scenario: Connectivity check supports an explicit deep mode
- **WHEN** an admin calls `POST providers/{name}/connectivity?deep=true`
- **THEN** the endpoint SHALL additionally execute the chat and embedding probes defined by the `provider-health-diagnostics` capability
- **AND** when `deep` is omitted or false, the endpoint SHALL NOT invoke a language or embedding model
- **AND** the persisted report SHALL include the skipped or executed deep checks

#### Scenario: Connectivity check does not leak credentials
- **WHEN** a provider failure contains credentials or credential-bearing headers in its response
- **THEN** the returned report, the persisted status, and the diagnostic log SHALL contain only sanitized text
- **AND** the API key resolved from the Halo Secret SHALL NOT appear in any diagnostic output

#### Scenario: Unknown provider is reported as not found
- **WHEN** an admin calls the connectivity endpoint for a provider resource that does not exist
- **THEN** the endpoint SHALL return HTTP 404
- **AND** it SHALL NOT create or modify any provider status
