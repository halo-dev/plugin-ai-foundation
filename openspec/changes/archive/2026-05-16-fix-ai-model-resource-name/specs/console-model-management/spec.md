## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Add model from provider
The Console UI SHALL allow admins to add a new `AiModel` inside the currently selected provider workspace.

#### Scenario: Add model from provider
- **WHEN** an admin clicks "添加模型"
- **AND** a modal form appears with the current provider pre-selected
- **AND** enters model ID (e.g., "gpt-4o") and display name (e.g., "GPT-4o")
- **AND** clicks save
- **THEN** the system SHALL create a new `AiModel` Extension via POST to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/models`)
- **AND** the backend SHALL generate a DNS-compliant `metadata.name` for the new model
- **AND** the backend SHALL validate that the referenced provider exists and the selected endpoint type is supported
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
- **AND** the backend SHALL keep the existing model resource identified by `{name}`
- **AND** the backend SHALL NOT reject the update solely because another model uses the same `spec.providerName` and `spec.modelId`

#### Scenario: Update model capabilities
- **WHEN** an admin edits a model and changes capability tags such as `vision`, `reasoning`, `function_calling`, or `embedding`
- **THEN** the system SHALL update the `AiModel` Extension
- **AND** the updated tags SHALL immediately affect the model list display and filtering
