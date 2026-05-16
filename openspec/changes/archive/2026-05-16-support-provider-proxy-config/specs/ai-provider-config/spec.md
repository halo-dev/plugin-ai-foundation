## ADDED Requirements

### Requirement: Provider proxy configuration

The system SHALL honor provider-level HTTP proxy settings when contacting upstream AI provider APIs.

#### Scenario: Upstream calls use configured proxy

- **WHEN** an `AiProvider` has both `spec.proxyHost` and `spec.proxyPort` configured
- **THEN** model discovery, connectivity checks, chat calls, and embedding calls for that provider MUST route upstream HTTP requests through the configured HTTP proxy

#### Scenario: No proxy preserves direct connectivity

- **WHEN** an `AiProvider` has neither `spec.proxyHost` nor `spec.proxyPort` configured
- **THEN** model discovery, connectivity checks, chat calls, and embedding calls for that provider MUST use the existing direct HTTP client behavior

#### Scenario: Incomplete proxy configuration is rejected

- **WHEN** a client creates or updates an `AiProvider` with only one of `spec.proxyHost` or `spec.proxyPort`
- **THEN** the system MUST reject the request with a validation error describing the missing proxy field

#### Scenario: Invalid proxy port is rejected

- **WHEN** a client creates or updates an `AiProvider` with `spec.proxyPort` outside the valid TCP port range
- **THEN** the system MUST reject the request with a validation error describing the invalid proxy port

#### Scenario: Proxy update refreshes cached clients

- **WHEN** an `AiProvider` is updated with new proxy settings
- **THEN** subsequent chat and embedding calls for that provider MUST use clients built from the updated proxy configuration
