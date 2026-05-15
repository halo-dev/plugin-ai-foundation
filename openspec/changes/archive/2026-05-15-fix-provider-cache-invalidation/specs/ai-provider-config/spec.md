## MODIFIED Requirements

### Requirement: Provider client caching
The system SHALL cache AI provider clients and refresh them when the corresponding `AiProvider` Extension is updated.

#### Scenario: Config update refreshes client
- **WHEN** an `AiProvider` Extension is updated with a new secret reference or a referenced Secret is rotated
- **THEN** subsequent calls using that provider SHALL use the updated credential

#### Scenario: Base URL update refreshes client
- **WHEN** an `AiProvider` Extension is updated with a new `baseUrl`
- **THEN** subsequent calls using that provider SHALL use the new base URL

#### Scenario: Provider deletion invalidates cache
- **WHEN** an `AiProvider` Extension is deleted
- **THEN** all cached `ChatModel` and `EmbeddingModel` instances for that provider SHALL be removed

#### Scenario: Secret rotation propagates to provider cache
- **WHEN** a Halo Secret referenced by one or more `AiProvider` resources is updated
- **THEN** the cached clients for all providers referencing that Secret SHALL be invalidated
