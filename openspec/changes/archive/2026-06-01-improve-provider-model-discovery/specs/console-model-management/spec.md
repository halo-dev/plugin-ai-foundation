## MODIFIED Requirements

### Requirement: Browse provider models
The Console UI SHALL allow browsing available models from a provider's API to simplify model addition.

#### Scenario: Browse and add from provider API
- **WHEN** an admin selects a provider and clicks "从供应商获取模型列表"
- **THEN** the system SHALL call the model listing endpoint for that provider
- **AND** display available models with their candidate model type and feature metadata
- **AND** display each model's discovery source and confidence when available
- **AND** group discovered models by model type
- **AND** allow the admin to select one or more models to add as `AiModel` entries

#### Scenario: Batch add discovered models
- **WHEN** an admin selects multiple discovered models from the provider API result
- **THEN** the UI SHALL create multiple `AiModel` entries in one batch workflow
- **AND** the UI SHALL allow selected models to span multiple displayed groups
- **AND** the UI SHALL submit each selected model's candidate model type, features, and discovery evidence
- **AND** the UI SHALL NOT submit a shared or per-model `group` value
- **AND** the UI SHALL NOT display a group default control during batch add from discovery

#### Scenario: Weak discovery confidence
- **WHEN** a discovered model was inferred from weak rules such as OpenAI-compatible model-name heuristics
- **THEN** the UI SHALL keep the inferred model in its candidate model type group
- **AND** the admin SHALL be able to correct the candidate profile before importing the model

#### Scenario: High confidence discovery remains editable
- **WHEN** a discovered model has high-confidence remote metadata
- **THEN** the UI SHALL use that metadata as the default candidate profile
- **AND** the admin SHALL still be able to correct the candidate profile before importing the model

#### Scenario: Search preserves grouped discovery workflow
- **WHEN** the admin enters a search keyword in the model discovery modal
- **THEN** the UI SHALL filter discovered models across all groups
- **AND** selection state SHALL remain preserved for models that are hidden by the current search
- **AND** the import action SHALL continue to import all selected models across groups
