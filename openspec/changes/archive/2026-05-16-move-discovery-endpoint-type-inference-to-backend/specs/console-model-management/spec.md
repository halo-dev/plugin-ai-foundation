## MODIFIED Requirements

### Requirement: Browse provider models
The Console UI SHALL allow browsing available models from a provider's API to simplify model addition.

#### Scenario: Browse and add from provider API
- **WHEN** an admin selects a provider and clicks "从供应商获取模型列表"
- **THEN** the system SHALL call the model listing endpoint for that provider
- **AND** display available models with their inferred capabilities (chat/embedding)
- **AND** display or retain each model's backend-recommended endpoint type when available
- **AND** allow the admin to select one or more models to add as `AiModel` entries

#### Scenario: Batch add discovered models
- **WHEN** an admin selects multiple discovered models from the provider API result
- **THEN** the UI SHALL create multiple `AiModel` entries in one batch workflow
- **AND** the admin MAY set shared defaults such as `group` before confirming
- **AND** the UI SHALL submit each model's backend-recommended `endpointType` when it is present
- **AND** the UI SHALL NOT infer `endpointType` from capability or endpoint type strings
- **AND** the UI SHALL NOT display a manual endpointType selector during batch add from discovery

#### Scenario: Batch add discovered models without a recommendation
- **WHEN** a discovered model does not include a backend-recommended endpoint type
- **THEN** the UI SHALL create the `AiModel` request without a client-derived `endpointType`
- **AND** the backend SHALL either apply a provider type default endpoint type or reject the request with a validation error
