## ADDED Requirements

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
