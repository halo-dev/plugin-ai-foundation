## ADDED Requirements

### Requirement: Provider embedding invocation behavior
Provider type implementations SHALL keep provider-specific embedding request mapping behind the provider type system and SHALL NOT require generic embedding service code to branch on concrete provider names.

#### Scenario: Provider applies supported embedding options
- **WHEN** an embedding request includes namespaced provider options for the current provider
- **THEN** the provider embedding implementation SHALL map supported options to the underlying provider request
- **AND** unsupported options SHALL be surfaced as warnings when the request otherwise succeeds

#### Scenario: Provider applies request headers
- **WHEN** an embedding request includes request-scoped headers
- **THEN** the provider embedding implementation SHALL apply those headers when its underlying adapter supports per-request headers
- **AND** unsupported per-request headers SHALL be surfaced as warnings

#### Scenario: Provider response diagnostics mapped
- **WHEN** a provider embedding response includes usage, response id, model, headers, or provider-native metadata
- **THEN** the provider embedding implementation SHALL map safe diagnostics into provider-neutral response fields

#### Scenario: Generic service remains provider-neutral
- **WHEN** a new provider type is added
- **THEN** embedding provider-specific option mapping SHALL be implemented in that provider type or its provider-owned helper
- **AND** `EmbeddingModelImpl` SHALL NOT need provider-specific conditionals for that provider
