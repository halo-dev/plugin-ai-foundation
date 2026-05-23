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
- **AND** `openailike` SHALL require manual `baseUrl` input because it has no default
- **AND** `ollama` MAY expose `baseUrl` for local endpoint customization while providing a default local URL

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

### Requirement: Console model selectors use aggregated options
The Console UI SHALL use aggregated model options for selector-style model lists that need provider display context or availability filtering.

#### Scenario: Render model picker options
- **WHEN** the Console renders a model picker outside the provider-scoped raw model management list
- **THEN** the UI SHALL fetch model options through the generated API client for `GET /model-options`
- **AND** each option label SHALL include the model display name or model ID and enough provider display context to distinguish same-named models
- **AND** each option value SHALL be `AiModel.metadata.name`

#### Scenario: Keep provider workspace on raw model resources
- **WHEN** the Console renders the selected provider's model management list
- **THEN** the UI SHALL continue using the raw `/models` endpoint with `fieldSelector=spec.providerName={selectedProvider}`
- **AND** the UI SHALL use model options only when joined provider display context or availability filtering is required

#### Scenario: Unavailable models in selectors
- **WHEN** a model option is unavailable
- **THEN** selector UIs SHALL either hide it when requesting `available=true` or render it as disabled with the backend-provided unavailable reason

### Requirement: Model resource name generation
The backend SHALL generate `AiModel.metadata.name` for models created through the Console API as a DNS-compliant resource name.

#### Scenario: Generate name for model IDs with unsafe characters
- **WHEN** an admin creates an `AiModel` whose `spec.modelId` contains characters such as `/`, `:`, `_`, `.`, spaces, or uppercase letters
- **THEN** the backend SHALL generate `metadata.name` using only lowercase letters, digits, and `-`
- **AND** the generated name SHALL start and end with a lowercase letter or digit
- **AND** the generated name SHALL be accepted by Halo resource-name validation

#### Scenario: Generate name for Ollama model tags
- **WHEN** an admin creates an `AiModel` with an Ollama-style `spec.modelId` such as `qwen2.5-coder:7b` or `llama3.1:8b`
- **THEN** the backend SHALL preserve a readable normalized form of the provider and model identifiers in `metadata.name`
- **AND** characters such as `.` and `:` SHALL NOT appear in the generated `metadata.name`

#### Scenario: Avoid normalized prefix collision
- **WHEN** two raw provider/model identifier pairs normalize to the same readable prefix
- **THEN** the generated `metadata.name` values SHALL include a suffix derived from the raw identifiers
- **AND** the generated names SHALL NOT rely on the normalized readable prefix as the only identity component

#### Scenario: Create duplicate provider and model configuration
- **WHEN** an admin creates an `AiModel`
- **AND** another `AiModel` already exists with the same `spec.providerName` and `spec.modelId`
- **THEN** the backend SHALL be allowed to create a separate `AiModel` resource
- **AND** the new resource SHALL receive a distinct DNS-compliant `metadata.name`
- **AND** the backend SHALL NOT reject the create request solely because the provider and model ID match an existing resource

#### Scenario: Resource name is the model identity
- **WHEN** an `AiModel` has been created
- **THEN** callers SHALL resolve that model by `AiModel.metadata.name`
- **AND** the backend SHALL NOT require `spec.providerName + spec.modelId` to be unique across all `AiModel` resources
