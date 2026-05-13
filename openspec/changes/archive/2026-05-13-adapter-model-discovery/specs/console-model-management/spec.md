## MODIFIED Requirements

### Requirement: Browse provider models
The Console UI SHALL allow browsing available models from a provider's API to simplify model addition.

#### Scenario: Browse and add from provider API
- **WHEN** an admin selects a provider and clicks "从供应商获取模型列表"
- **THEN** the system SHALL call the model listing endpoint for that provider
- **AND** display available models with their inferred capabilities (chat/embedding)
- **AND** allow the admin to select one or more models to add as `AiModel` entries

#### Scenario: Batch add discovered models
- **WHEN** an admin selects multiple discovered models from the provider API result
- **THEN** the UI SHALL create multiple `AiModel` entries in one batch workflow
- **AND** the admin MAY set shared defaults such as `group` before confirming
- **AND** the `endpointType` SHALL be automatically inferred from each model's `capabilities` field:
  - models with `CHAT` capability → chat endpointType (e.g., `openai-chat`, `ollama-chat`)
  - models with `EMBEDDING` capability → embedding endpointType (e.g., `openai-embedding`)
- **AND** the UI SHALL NOT display a manual endpointType selector during batch add from discovery
