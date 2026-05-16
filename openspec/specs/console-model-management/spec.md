## Purpose

Define the Console UI behavior for managing AI providers and models, including provider list, model list, CRUD operations, and utility features.
## Requirements
### Requirement: Console navigation menu
The plugin SHALL register a Console route under the system menu group for managing AI providers and models.

#### Scenario: Menu registration
- **WHEN** an admin user opens the Halo Console
- **THEN** there SHALL be a menu item named "AI 模型配置" under the system group
- **AND** clicking it SHALL navigate to the provider and model management page

### Requirement: Provider list view
The Console UI SHALL display a list of all `AiProvider` Extensions with their type, display name, and status.

#### Scenario: View all providers
- **WHEN** an admin opens the AI model configuration page
- **THEN** the system SHALL fetch all providers via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers`)
- **AND** the response SHALL be a non-paginated array sorted by `metadata.creationTimestamp` descending
- **AND** each item SHALL show `providerType`, `displayName`, `enabled` status, and `status.phase`

### Requirement: Provider workspace layout
The Console UI SHALL use a provider-centric master-detail workspace that remains usable across desktop and mobile viewport widths.

#### Scenario: Aggregated provider workspace
- **WHEN** an admin opens the AI model configuration page on a desktop-width viewport
- **THEN** the left side SHALL display the provider list
- **AND** selecting a provider SHALL open a right-side detail workspace for that provider
- **AND** the workspace SHALL include both provider configuration and the models belonging to that provider
- **AND** switching to a different provider SHALL refresh the model list to show only models belonging to the newly selected provider

#### Scenario: Mobile provider workspace
- **WHEN** an admin opens the AI model configuration page on a mobile-width viewport
- **THEN** the provider list SHALL NOT reserve a fixed-width left column
- **AND** the provider list SHALL be displayed above the provider detail workspace
- **AND** the provider detail workspace SHALL remain visible and usable without horizontal compression
- **AND** the provider list and provider detail workspace SHALL each remain scrollable when their content exceeds the available height

#### Scenario: Compact provider detail content
- **WHEN** an admin views provider detail content on a narrow viewport
- **THEN** provider action controls SHALL wrap or stack instead of causing horizontal overflow
- **AND** provider metadata fields SHALL stack or reduce columns so labels and values remain readable
- **AND** model list content SHALL remain accessible without being squeezed by the provider list

### Requirement: Create new provider
The Console UI SHALL allow admins to create a new `AiProvider` Extension by selecting a provider type and filling in configuration fields.

#### Scenario: Create OpenAI provider
- **WHEN** an admin clicks "添加模型供应商"
- **AND** selects "OpenAI" as the provider type
- **AND** the display name field auto-populates with "OpenAI"
- **AND** binds a Halo Secret containing the API key
- **AND** clicks save
- **THEN** the system SHALL create a new `AiProvider` Extension via POST to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers`)
- **AND** the backend SHALL validate that the selected `providerType` is supported
- **AND** the new provider SHALL appear in the list

#### Scenario: Create AiHubMix provider without manual base URL
- **WHEN** an admin clicks "添加模型供应商"
- **AND** selects "AiHubMix" as the provider type
- **AND** binds a Halo Secret containing the API key
- **AND** clicks save
- **THEN** the system SHALL create a new `AiProvider` Extension using the built-in AiHubMix preset
- **AND** the admin SHALL NOT be required to manually enter AiHubMix's API base URL

#### Scenario: Auto-fill display name from provider type
- **WHEN** an admin selects a provider type during creation
- **THEN** the display name field SHALL auto-populate with the selected provider type's display name
- **AND** the admin MAY override the auto-filled value before saving
- **AND** auto-fill SHALL NOT overwrite a value the admin has already entered

### Requirement: Edit provider configuration
The Console UI SHALL allow admins to edit an existing `AiProvider`'s configuration.

#### Scenario: Update API key
- **WHEN** an admin clicks edit on an existing OpenAI provider
- **AND** changes the bound Halo Secret or replaces its referenced key
- **AND** clicks save
- **THEN** the system SHALL update the `AiProvider` Extension via PUT to the Extension API (`/apis/aifoundation.halo.run/v1alpha1/aiproviders/{name}`)
- **AND** subsequent AI calls SHALL use the new API key

#### Scenario: Edit structured provider connection fields
- **WHEN** an admin edits a provider
- **THEN** the form SHALL expose structured fields such as `baseUrl`, `apiKeySecretName`, and `enabled`
- **AND** advanced provider-specific fields MAY be edited through an additional advanced settings area backed by `spec.config`

#### Scenario: Built-in preset hides custom base URL
- **WHEN** an admin edits a built-in provider such as `aihubmix` or `siliconflow`
- **THEN** the form SHOULD prioritize the preset experience of editing API key and basic metadata
- **AND** the admin SHALL NOT be required to fill a custom `baseUrl`
- **AND** only the `openailike` provider type SHALL require manual `baseUrl` input

### Requirement: API key masking and testing
The Console UI SHALL protect sensitive provider keys while still allowing connectivity verification.

#### Scenario: Masked API key display
- **WHEN** an admin opens a provider detail page
- **THEN** the UI SHALL display the bound Halo Secret name and masked preview by default
- **AND** the UI SHALL allow opening a Secret edit or replacement flow when needed

### Requirement: Delete provider
The Console UI SHALL allow admins to delete an `AiProvider` Extension.

#### Scenario: Delete provider
- **WHEN** an admin clicks delete on a provider
- **AND** confirms the deletion in a warning dialog
- **THEN** the system SHALL call DELETE on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers/{name}`)
- **AND** the backend SHALL allow deletion even if the provider has associated `AiModel` entries
- **AND** the associated models SHALL be automatically deleted by the cascade delete reconciler
- **AND** the provider SHALL disappear from the list

### Requirement: Model list view
The Console UI SHALL display provider-scoped `AiModel` entries in the selected provider workspace.

#### Scenario: View all models
- **WHEN** an admin selects a provider
- **THEN** the system SHALL fetch models via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`) with `fieldSelector=spec.providerName={selectedProvider}`
- **AND** the response SHALL be a non-paginated array sorted by `metadata.creationTimestamp` descending
- **AND** each item SHALL show the model display name and underlying `providerResourceName/modelId` reference, where `providerResourceName = AiProvider.metadata.name`
- **AND** each item SHALL show its group and capability tags when available

### Requirement: Model grouping and filtering
The Console UI SHALL support browsing models with group and capability context.

#### Scenario: Grouped model display
- **WHEN** the selected provider has models with `spec.group`
- **THEN** the UI SHALL group models by that value in collapsible sections

#### Scenario: Filter models by keyword or capability
- **WHEN** an admin enters a search term or chooses a capability filter
- **THEN** the UI SHALL narrow the displayed models within the selected provider workspace
- **AND** filtering SHALL work with model ID, display name, group, and capability tags

### Requirement: Add model from provider
The Console UI SHALL allow admins to add a new `AiModel` inside the currently selected provider workspace.

#### Scenario: Add model from provider
- **WHEN** an admin clicks "添加模型"
- **AND** a modal form appears with the current provider pre-selected
- **AND** enters model ID (e.g., "gpt-4o") and display name (e.g., "GPT-4o")
- **AND** clicks save
- **THEN** the system SHALL create a new `AiModel` Extension via POST to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`)
- **AND** the backend SHALL validate that no other model with the same `providerName` and `modelId` already exists
- **AND** the new model SHALL appear in the list

#### Scenario: Add model metadata
- **WHEN** an admin creates or edits a model
- **THEN** the form SHALL support editing `group`, `capabilities`, `endpointType`, and `supportedTextDelta`
- **AND** these values SHALL be persisted to the `AiModel` Extension

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

### Requirement: Edit model
The Console UI SHALL allow admins to edit an existing `AiModel`'s metadata.

#### Scenario: Update model display name
- **WHEN** an admin clicks edit on a model
- **AND** changes the display name
- **AND** clicks save
- **THEN** the system SHALL update the `AiModel` Extension via PUT to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models/{name}`)
- **AND** the backend SHALL validate that the updated `providerName` + `modelId` combination does not conflict with another model

#### Scenario: Update model capabilities
- **WHEN** an admin edits a model and changes capability tags such as `vision`, `reasoning`, `function_calling`, or `embedding`
- **THEN** the system SHALL update the `AiModel` Extension
- **AND** the updated tags SHALL immediately affect the model list display and filtering

### Requirement: Delete model
The Console UI SHALL allow admins to delete an `AiModel` Extension.

#### Scenario: Delete model
- **WHEN** an admin clicks delete on a model
- **AND** confirms the deletion in a warning dialog
- **THEN** the system SHALL delete the `AiModel` Extension via DELETE to the Extension API (`/apis/aifoundation.halo.run/v1alpha1/aimodels/{name}`)
- **AND** the model SHALL disappear from the list

### Requirement: Connectivity testing
The Console UI SHALL provide a manual connectivity test button for each provider.

#### Scenario: Test OpenAI connectivity
- **WHEN** an admin clicks "测试连通性" on an OpenAI provider
- **THEN** the system SHALL call the connectivity test endpoint
- **AND** display success if the API key is valid
- **AND** display an error message if the API key is invalid or the service is unreachable

### Requirement: Test chat debugging
The Console UI SHALL provide a test chat entry that reuses the backend `test-chat` endpoint.

#### Scenario: Test chat with selected model
- **WHEN** an admin selects a configured `AiModel` and enters a prompt
- **THEN** the UI SHALL call the backend `test-chat` endpoint for that `providerResourceName/modelId`
- **AND** display the returned content and available metadata

