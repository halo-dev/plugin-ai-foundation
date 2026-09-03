## ADDED Requirements

### Requirement: Provider-owned adapter metadata
The provider type registry SHALL expose the provider-owned adapters, supported model domains, endpoint requirements, and evidence-backed default capabilities supplied by each backend provider type.

#### Scenario: Console renders built-in provider
- **WHEN** the Console loads provider type metadata
- **THEN** it SHALL render the adapters and capabilities returned by the backend
- **AND** SHALL NOT infer a hardcoded OpenAI-compatible adapter for a built-in provider

#### Scenario: Provider adds a protocol adapter
- **WHEN** a backend provider adds a documented protocol adapter
- **THEN** the Console SHALL make it available through generated registry metadata without adding a hardcoded provider list

#### Scenario: Registry presents an adapter
- **WHEN** the registry returns adapter metadata
- **THEN** its display name and description SHALL come from the adapter's explicit protocol metadata
- **AND** SHALL NOT be inferred from the serialized adapter value, model identifier, or provider name

#### Scenario: Model domain has one adapter
- **WHEN** the selected provider and model domain expose exactly one adapter
- **THEN** the Console SHALL auto-select that adapter and omit the interface selector
- **AND** SHALL submit the selected adapter with the model state

#### Scenario: Model domain has multiple adapters
- **WHEN** the selected provider and model domain expose more than one adapter
- **THEN** the Console SHALL show an interface selector using backend-provided labels and descriptions
- **AND** SHALL default to the adapter marked as recommended
