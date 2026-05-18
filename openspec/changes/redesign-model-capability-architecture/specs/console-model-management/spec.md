## MODIFIED Requirements

### Requirement: Model list view
The Console UI SHALL display provider-scoped `AiModel` entries in the selected provider workspace.

#### Scenario: View all models
- **WHEN** an admin selects a provider
- **THEN** the system SHALL fetch models via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`) with `fieldSelector=spec.providerName={selectedProvider}`
- **AND** the response SHALL be a non-paginated array sorted by `metadata.creationTimestamp` descending
- **AND** each item SHALL show the model display name and underlying `providerResourceName/modelId` reference, where `providerResourceName = AiProvider.metadata.name`
- **AND** each item SHALL show its group, model type, and feature tags when available
- **AND** each item SHALL NOT present internal adapter type as the primary capability label

### Requirement: Model grouping and filtering
The Console UI SHALL support browsing models with group, model type, and feature context.

#### Scenario: Grouped model display
- **WHEN** the selected provider has models with `spec.group`
- **THEN** the UI SHALL group models by that value in collapsible sections

#### Scenario: Filter models by keyword or model profile
- **WHEN** an admin enters a search term, chooses a model type filter, or chooses a feature filter
- **THEN** the UI SHALL narrow the displayed models within the selected provider workspace
- **AND** filtering SHALL work with model ID, display name, group, model type, and feature tags

### Requirement: Add model from provider
The Console UI SHALL allow admins to add a new `AiModel` inside the currently selected provider workspace using model purpose and feature fields instead of manual endpoint configuration.

#### Scenario: Add language model from provider
- **WHEN** an admin clicks "添加模型"
- **AND** a modal form appears with the current provider pre-selected
- **AND** enters model ID (e.g., "gpt-4o") and display name (e.g., "GPT-4o")
- **AND** selects `language` as the model type
- **AND** optionally selects features such as `streaming`, `vision`, `tool-call`, `structured-output`, or `reasoning`
- **AND** clicks save
- **THEN** the system SHALL create a new `AiModel` Extension via POST to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`)
- **AND** the backend SHALL generate a DNS-compliant `metadata.name` for the new model
- **AND** the backend SHALL validate that the referenced provider exists and that a supported internal adapter can be resolved
- **AND** the new model SHALL appear in the list

#### Scenario: Add model metadata
- **WHEN** an admin creates or edits a model
- **THEN** the form SHALL support editing `group`, `modelType`, and `features`
- **AND** these values SHALL be persisted to the `AiModel` Extension
- **AND** the normal form SHALL NOT require manual `adapterType` selection

### Requirement: Browse provider models
The Console UI SHALL allow browsing available models from a provider's API to simplify model addition.

#### Scenario: Browse and add from provider API
- **WHEN** an admin selects a provider and clicks "从供应商获取模型列表"
- **THEN** the system SHALL call the model listing endpoint for that provider
- **AND** display available models with their candidate model type and feature metadata
- **AND** display each model's discovery source and confidence when available
- **AND** display the backend-recommended internal adapter only as advanced metadata
- **AND** allow the admin to select one or more models to add as `AiModel` entries

#### Scenario: Batch add discovered models
- **WHEN** an admin selects multiple discovered models from the provider API result
- **THEN** the UI SHALL create multiple `AiModel` entries in one batch workflow
- **AND** the admin MAY set shared defaults such as `group` before confirming
- **AND** the UI SHALL submit each selected model's candidate model type, features, discovery evidence, and backend-recommended adapter when present
- **AND** the UI SHALL NOT infer adapter type from capability or endpoint strings
- **AND** the UI SHALL NOT display a manual adapter selector during batch add from discovery unless the backend reports multiple valid adapters or no safe adapter recommendation

#### Scenario: Batch add discovered models without a recommendation
- **WHEN** a discovered model does not include a backend-recommended adapter type
- **THEN** the UI SHALL create the `AiModel` request without a client-derived adapter type
- **AND** the backend SHALL either apply a provider type adapter recommendation or reject the request with a validation error

#### Scenario: Weak discovery confidence
- **WHEN** a discovered model was inferred from weak rules such as OpenAI-compatible model-name heuristics
- **THEN** the UI SHALL indicate that the model type and features should be confirmed
- **AND** the admin SHALL be able to correct the candidate profile before importing the model

### Requirement: Edit model
The Console UI SHALL allow admins to edit an existing `AiModel`'s metadata.

#### Scenario: Update model display name
- **WHEN** an admin clicks edit on a model
- **AND** changes the display name
- **AND** clicks save
- **THEN** the system SHALL update the `AiModel` Extension via PUT to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models/{name}`)
- **AND** the backend SHALL keep the existing model resource identified by `{name}`
- **AND** the backend SHALL NOT reject the update solely because another model uses the same `spec.providerName` and `spec.modelId`

#### Scenario: Update model profile
- **WHEN** an admin edits a model and changes `modelType` or feature tags such as `vision`, `reasoning`, `tool-call`, `structured-output`, or `streaming`
- **THEN** the system SHALL update the `AiModel` Extension
- **AND** the updated model profile SHALL immediately affect the model list display, filtering, and default-slot eligibility
