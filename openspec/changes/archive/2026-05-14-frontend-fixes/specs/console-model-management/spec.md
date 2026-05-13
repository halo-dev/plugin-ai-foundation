## MODIFIED Requirements

### Requirement: Provider workspace layout
The Console UI SHALL use a provider-centric master-detail workspace.

#### Scenario: Aggregated provider workspace
- **WHEN** an admin opens the AI model configuration page
- **THEN** the left side SHALL display the provider list
- **AND** selecting a provider SHALL open a right-side detail workspace for that provider
- **AND** the workspace SHALL include both provider configuration and the models belonging to that provider
- **AND** switching to a different provider SHALL refresh the model list to show only models belonging to the newly selected provider

### Requirement: Delete provider
The Console UI SHALL allow admins to delete an `AiProvider` Extension.

#### Scenario: Delete provider
- **WHEN** an admin clicks delete on a provider
- **AND** confirms the deletion in a warning dialog
- **THEN** the system SHALL delete the `AiProvider` Extension via DELETE to the Extension API
- **AND** the provider SHALL disappear from the list

#### Scenario: Block deleting provider with models
- **WHEN** an admin clicks delete on a provider that still has associated `AiModel` entries
- **THEN** the UI SHALL prevent deletion
- **AND** display a toast message explaining that dependent models must be removed first

### Requirement: Add model from provider
The Console UI SHALL allow admins to add a new `AiModel` inside the currently selected provider workspace.

#### Scenario: Add model from provider
- **WHEN** an admin clicks "添加模型"
- **AND** a modal form appears with the current provider pre-selected
- **AND** enters model ID (e.g., "gpt-4o") and display name (e.g., "GPT-4o")
- **AND** clicks save
- **THEN** the system SHALL create a new `AiModel` Extension via POST to the Extension API
- **AND** the new model SHALL appear in the list

#### Scenario: Add model metadata
- **WHEN** an admin creates or edits a model
- **THEN** the form SHALL support editing `group`, `capabilities`, `endpointType`, and `supportedTextDelta`
- **AND** these values SHALL be persisted to the `AiModel` Extension

### Requirement: Delete model
The Console UI SHALL allow admins to delete an `AiModel` Extension.

#### Scenario: Delete model
- **WHEN** an admin clicks delete on a model
- **AND** confirms the deletion in a warning dialog
- **THEN** the system SHALL delete the `AiModel` Extension via DELETE to the Extension API
- **AND** the model SHALL disappear from the list

### Requirement: Create new provider
The Console UI SHALL allow admins to create a new `AiProvider` Extension by selecting a provider type and filling in configuration fields.

#### Scenario: Create OpenAI provider
- **WHEN** an admin clicks "添加模型供应商"
- **AND** selects "OpenAI" as the provider type
- **AND** the display name field auto-populates with "OpenAI"
- **AND** binds a Halo Secret containing the API key
- **AND** clicks save
- **THEN** the system SHALL create a new `AiProvider` Extension via POST to the Extension API
- **AND** the new provider SHALL appear in the list

#### Scenario: Auto-fill display name from provider type
- **WHEN** an admin selects a provider type during creation
- **THEN** the display name field SHALL auto-populate with the selected provider type's display name
- **AND** the admin MAY override the auto-filled value before saving
- **AND** auto-fill SHALL NOT overwrite a value the admin has already entered
