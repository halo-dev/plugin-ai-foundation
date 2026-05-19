## MODIFIED Requirements

### Requirement: Model list view
The Console UI SHALL display provider-scoped `AiModel` entries in the selected provider workspace.

#### Scenario: View all models
- **WHEN** an admin selects a provider
- **THEN** the system SHALL fetch models via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`) with `fieldSelector=spec.providerName={selectedProvider}`
- **AND** the response SHALL be a non-paginated array sorted by `metadata.creationTimestamp` descending
- **AND** each item SHALL show the model display name and underlying `providerResourceName/modelId` reference, where `providerResourceName = AiProvider.metadata.name`
- **AND** each item SHALL show model type and feature tags when available
- **AND** each item MUST NOT show or depend on `spec.group`

### Requirement: Model grouping and filtering
The Console UI SHALL support browsing models with typed model profile context and SHALL NOT use a free-form model group axis.

#### Scenario: Flat model display
- **WHEN** the selected provider has one or more models
- **THEN** the UI SHALL render the models without grouping them by `spec.group`
- **AND** the UI SHALL NOT create a default "未分组" section

#### Scenario: Filter models by keyword or model profile
- **WHEN** an admin enters a search term, chooses a model type filter, or chooses a feature filter
- **THEN** the UI SHALL narrow the displayed models within the selected provider workspace
- **AND** filtering SHALL work with model ID, display name, model type, and feature tags
- **AND** filtering MUST NOT depend on `spec.group`

### Requirement: Add model from provider
The Console UI SHALL allow admins to add a new `AiModel` inside the currently selected provider workspace using model purpose and feature fields instead of free-form grouping.

#### Scenario: Add model from provider
- **WHEN** an admin clicks "添加模型"
- **AND** a modal form appears with the current provider pre-selected
- **AND** enters model ID (e.g., "gpt-4o") and display name (e.g., "GPT-4o")
- **AND** selects a model type and optional feature tags
- **AND** clicks save
- **THEN** the system SHALL create a new `AiModel` Extension via POST to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`)
- **AND** the backend SHALL generate a DNS-compliant `metadata.name` for the new model
- **AND** the backend SHALL validate that the referenced provider exists and the selected model profile is supported
- **AND** the new model SHALL appear in the list

#### Scenario: Add model metadata
- **WHEN** an admin creates or edits a model
- **THEN** the form SHALL support editing `modelType` and `features`
- **AND** these values SHALL be persisted to the `AiModel` Extension
- **AND** the form MUST NOT include a `group` input

### Requirement: Browse provider models
The Console UI SHALL allow browsing available models from a provider's API to simplify model addition.

#### Scenario: Browse and add from provider API
- **WHEN** an admin selects a provider and clicks "从供应商获取模型列表"
- **THEN** the system SHALL call the model listing endpoint for that provider
- **AND** display available models with their candidate model type and feature metadata
- **AND** display each model's discovery source and confidence when available
- **AND** allow the admin to select one or more models to add as `AiModel` entries

#### Scenario: Batch add discovered models
- **WHEN** an admin selects multiple discovered models from the provider API result
- **THEN** the UI SHALL create multiple `AiModel` entries in one batch workflow
- **AND** the UI SHALL submit each selected model's candidate model type, features, and discovery evidence
- **AND** the UI SHALL NOT submit a shared or per-model `group` value
- **AND** the UI SHALL NOT display a group default control during batch add from discovery

#### Scenario: Weak discovery confidence
- **WHEN** a discovered model was inferred from weak rules such as OpenAI-compatible model-name heuristics
- **THEN** the UI SHALL indicate that the model type and features should be confirmed
- **AND** the admin SHALL be able to correct the candidate profile before importing the model
