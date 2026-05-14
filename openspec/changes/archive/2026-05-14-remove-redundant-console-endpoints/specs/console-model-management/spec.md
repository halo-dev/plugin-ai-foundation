## MODIFIED Requirements

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

### Requirement: Delete provider
The Console UI SHALL allow admins to delete an `AiProvider` Extension.

#### Scenario: Delete provider
- **WHEN** an admin clicks delete on a provider
- **AND** confirms the deletion in a warning dialog
- **THEN** the system SHALL call DELETE on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers/{name}`)
- **AND** the backend SHALL block deletion if the provider still has associated `AiModel` entries
- **AND** the provider SHALL disappear from the list

#### Scenario: Block deleting provider with models
- **WHEN** an admin clicks delete on a provider that still has associated `AiModel` entries
- **THEN** the backend SHALL reject the request with a 400 error
- **AND** the UI SHALL display a toast message explaining that dependent models must be removed first

### Requirement: Provider list view
The Console UI SHALL display a list of all `AiProvider` Extensions with their type, display name, and status.

#### Scenario: View all providers
- **WHEN** an admin opens the AI model configuration page
- **THEN** the system SHALL fetch all providers via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers`)
- **AND** the response SHALL be a non-paginated array
- **AND** each item SHALL show `providerType`, `displayName`, `enabled` status, and `status.phase`

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

### Requirement: Model list view
The Console UI SHALL display provider-scoped `AiModel` entries in the selected provider workspace.

#### Scenario: View all models
- **WHEN** an admin selects a provider
- **THEN** the system SHALL fetch models via GET on the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`) with `fieldSelector=spec.providerName={selectedProvider}`
- **AND** the response SHALL be a non-paginated array
- **AND** each item SHALL show the model display name and underlying `providerResourceName/modelId` reference, where `providerResourceName = AiProvider.metadata.name`
- **AND** each item SHALL show its group and capability tags when available

## REMOVED Requirements

### Requirement: Console get/delete endpoints for models and get/update endpoints for providers
**Reason**: These are pure pass-throughs to `ReactiveExtensionClient` with no added validation or logic. Halo's Extension API provides identical behavior.
**Migration**:
- Get model by name → `GET /apis/aifoundation.halo.run/v1alpha1/aimodels/{name}`
- Delete model → `DELETE /apis/aifoundation.halo.run/v1alpha1/aimodels/{name}`
- Get provider by name → `GET /apis/aifoundation.halo.run/v1alpha1/aiproviders/{name}`
- Update provider → `PUT /apis/aifoundation.halo.run/v1alpha1/aiproviders/{name}`
