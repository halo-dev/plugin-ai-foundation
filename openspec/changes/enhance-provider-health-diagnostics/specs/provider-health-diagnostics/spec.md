## ADDED Requirements

### Requirement: Structured provider health checks
The system SHALL represent a provider health check as an ordered set of per-target results instead of a single phase and message.

#### Scenario: Check result includes per-target entries
- **WHEN** an administrator triggers a connectivity check for an `AiProvider`
- **THEN** the result SHALL contain one entry for each of `CONFIGURATION`, `NETWORK`, `AUTHENTICATION`, `MODEL_DISCOVERY`, `CHAT`, and `EMBEDDING`
- **AND** each entry SHALL report `result` as `PASSED`, `FAILED`, or `SKIPPED`
- **AND** each entry SHALL include `checkedAt`, and when available `message`, `httpStatus`, and `latencyMs`
- **AND** a `FAILED` entry SHALL include a failure category
- **AND** a `SKIPPED` entry SHALL include a skip reason

#### Scenario: All executed checks pass
- **WHEN** every executed check passes and no executed check fails
- **THEN** `status.phase` SHALL be `OK`
- **AND** skipped checks SHALL NOT by themselves change the phase
- **AND** `status.failureCategory` SHALL be `null`

#### Scenario: At least one executed check fails
- **WHEN** one or more executed checks fail
- **THEN** `status.phase` SHALL be `ERROR`
- **AND** `status.failureCategory` SHALL be the category of the primary failed check

### Requirement: Failure classification
The system SHALL classify failures into stable categories that distinguish configuration, authentication, network, rate limiting, provider failure, and unsupported capability.

#### Scenario: Invalid configuration is reported without a network request
- **WHEN** an `AiProvider` has a missing required base URL, an invalid base URL, or an invalid proxy configuration
- **THEN** the `CONFIGURATION` check SHALL fail with category `CONFIGURATION`
- **AND** the system SHALL NOT send a provider request for that check run
- **AND** `NETWORK`, `AUTHENTICATION`, and `MODEL_DISCOVERY` SHALL be `SKIPPED` with reason `BLOCKED_BY_PREVIOUS_FAILURE`

#### Scenario: Broken API key binding is a configuration failure
- **WHEN** `spec.apiKeySecretName` is set but the Secret does not exist, has no data, or has no `api-key` entry
- **THEN** the `AUTHENTICATION` check SHALL fail with category `CONFIGURATION`
- **AND** the system SHALL still update `AiProvider.status`
- **AND** the response SHALL NOT fail with an unhandled server error

#### Scenario: Network failure stops dependent checks
- **WHEN** the discovery request fails with a DNS, connection, TLS, proxy, or timeout error
- **THEN** the `NETWORK` check SHALL fail with category `NETWORK`
- **AND** `AUTHENTICATION` and `MODEL_DISCOVERY` SHALL be `SKIPPED` with reason `BLOCKED_BY_PREVIOUS_FAILURE`

#### Scenario: Authentication failure is distinguished from network failure
- **WHEN** the discovery request receives HTTP 401 or 403
- **THEN** the `NETWORK` check SHALL pass
- **AND** the `AUTHENTICATION` check SHALL fail with category `AUTHENTICATION`
- **AND** `MODEL_DISCOVERY` SHALL be `SKIPPED` with reason `BLOCKED_BY_PREVIOUS_FAILURE`

#### Scenario: Endpoint and capability failures are distinguished
- **WHEN** the discovery request receives HTTP 404 or a response indicating an unsupported operation
- **THEN** `NETWORK` SHALL pass
- **AND** the `MODEL_DISCOVERY` check SHALL fail with category `CONFIGURATION` or `CAPABILITY_UNSUPPORTED`
- **AND** when the response is HTTP 405 or 501, the category SHALL be `CAPABILITY_UNSUPPORTED`

#### Scenario: Provider-side failures are classified
- **WHEN** the discovery request receives HTTP 429
- **THEN** the `MODEL_DISCOVERY` check SHALL fail with category `RATE_LIMITED`
- **AND** when the response is HTTP 5xx, the category SHALL be `PROVIDER_ERROR`
- **AND** unclassifiable failures SHALL use category `UNKNOWN` without discarding the sanitized raw summary

### Requirement: Health result persistence
The system SHALL persist the latest health check report on `AiProvider.status` and expose it through the Console API.

#### Scenario: Latest run replaces previous checks
- **WHEN** a connectivity check completes
- **THEN** `AiProvider.status.checks` SHALL contain the checks executed by that run
- **AND** `AiProvider.status.lastCheckedAt` SHALL be the completion time
- **AND** the persisted report SHALL be the same report returned by the endpoint

#### Scenario: Status write does not overwrite concurrent spec changes
- **WHEN** the provider resource is modified while a check is running
- **THEN** the system SHALL re-read the current resource before writing status
- **AND** it SHALL NOT replace a concurrently updated `spec`
- **AND** it SHALL retry a bounded number of times on an optimistic locking conflict

#### Scenario: Configuration changes invalidate a previous result
- **WHEN** an administrator updates a health-affecting provider field such as `providerType`, `baseUrl`, an endpoint path, `apiKeySecretName`, or proxy settings
- **THEN** `status.phase` SHALL become `UNKNOWN`
- **AND** `status.checks` SHALL become empty
- **AND** `status.failureCategory` SHALL be `null`
- **AND** updating only the display name or enabled flag SHALL NOT invalidate the result

### Requirement: Quick and deep connectivity checks
The system SHALL keep the default check free of model invocations and SHALL require an explicit request for chat and embedding probes.

#### Scenario: Default check does not invoke models
- **WHEN** an administrator calls the connectivity endpoint without requesting a deep check
- **THEN** the `CHAT` and `EMBEDDING` checks SHALL be `SKIPPED` with reason `NOT_REQUESTED`
- **AND** the system SHALL NOT invoke a language or embedding model

#### Scenario: Deep check probes configured domains
- **WHEN** an administrator requests a deep check
- **AND** the provider has an enabled language model
- **THEN** the `CHAT` check SHALL perform one minimal non-streaming generation request against that model
- **AND** when the provider has an enabled embedding model, the `EMBEDDING` check SHALL perform one minimal embedding request
- **AND** a successful probe SHALL mark the check `PASSED`
- **AND** a failed probe SHALL mark the check `FAILED` with a classified category and a sanitized raw summary

#### Scenario: Missing model or disabled provider skips deep checks
- **WHEN** a deep check is requested but the provider has no enabled model for that domain
- **THEN** the corresponding check SHALL be `SKIPPED` with reason `NO_MODEL`
- **AND** when the provider is disabled, deep checks SHALL be `SKIPPED` with reason `PROVIDER_DISABLED`
- **AND** the Console SHALL warn that a deep check may consume provider quota

#### Scenario: Check execution is bounded
- **WHEN** a provider endpoint does not respond
- **THEN** the check SHALL time out within the bounded check timeout
- **AND** the failure SHALL be reported as category `NETWORK`
- **AND** the endpoint SHALL NOT wait for the provider client's long-running response timeout

### Requirement: Credential-safe diagnostics
The system SHALL sanitize and bound all diagnostic text before returning it or persisting it.

#### Scenario: Provider error body is sanitized
- **WHEN** a provider error body contains an API key, an `Authorization` header value, a token query parameter, or URL userinfo
- **THEN** the returned and persisted `message` and `detail` SHALL NOT contain the credential
- **AND** the sanitized raw summary SHALL be retained for developer investigation
- **AND** `status.message` SHALL be bounded to a short summary and each `detail` SHALL be bounded to a fixed maximum length

#### Scenario: Credentials never enter the report
- **WHEN** a check reads the API key from a Halo Secret
- **THEN** the key value SHALL NOT appear in the response, `AiProvider.status`, or diagnostic logs
- **AND** the report SHALL NOT include request headers

### Requirement: Console provider health presentation
The Chinese Console SHALL present provider health in the provider list and in the provider detail's status information row.

#### Scenario: Provider list shows abnormal source
- **WHEN** a provider's phase is `ERROR`
- **THEN** the provider list item SHALL show an abnormal status with the failure category
- **AND** it SHALL show a truncated safe error summary
- **AND** a provider that has never been checked SHALL be shown as unchecked rather than normal

#### Scenario: Provider detail shows per-check diagnosis
- **WHEN** an administrator opens a provider detail
- **THEN** the detail SHALL show the last check time, overall status, and failure category
- **AND** the per-check diagnosis SHALL live in the detail's status information row, collapsed to a one-line summary by default
- **AND** expanding it SHALL show every check target with its result, HTTP status, and latency where available
- **AND** it SHALL show an actionable Chinese hint for the failure category
- **AND** it SHALL show the sanitized raw detail only after the administrator expands it

#### Scenario: Check completion refreshes the page
- **WHEN** a connectivity check completes successfully
- **THEN** the provider detail SHALL reflect the newly returned status without requiring a manual reload
- **AND** the provider list SHALL reflect the same status

#### Scenario: Model list does not repeat provider warnings
- **WHEN** a provider's phase is `ERROR`
- **THEN** the model list SHALL NOT render its own warning banner
- **AND** the abnormal state SHALL remain visible in the provider detail's status information row

#### Scenario: Deep check is clearly marked
- **WHEN** the Console offers the deep check action
- **THEN** the action SHALL indicate that it performs real model calls and may consume quota
- **AND** the default connectivity action SHALL NOT perform model calls

### Requirement: Released contracts remain compatible
The system SHALL keep released public and persisted contracts compatible while adding health diagnostics.

#### Scenario: Public SDK is unchanged
- **WHEN** a consumer plugin reads `ProviderInfo`
- **THEN** `phase` and `lastCheckedAt` SHALL retain their existing meaning
- **AND** no failure category, raw detail, or Spring AI type SHALL be added to the public SDK

#### Scenario: Existing providers remain readable
- **WHEN** an `AiProvider` created before this change is read
- **THEN** missing `checks` and `failureCategory` SHALL be treated as an empty check list and no failure
- **AND** no migration step SHALL be required

#### Scenario: Generated Console client is regenerated
- **WHEN** the Console API shape changes
- **THEN** the OpenAPI document and TypeScript client SHALL be regenerated
- **AND** generated files SHALL NOT be edited manually
