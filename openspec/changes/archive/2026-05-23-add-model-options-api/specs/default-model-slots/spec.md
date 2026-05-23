## MODIFIED Requirements

### Requirement: Console default model settings
The Console SHALL provide a super-admin settings surface for choosing default model slots.

#### Scenario: Slot selector filters by model type
- **WHEN** an admin configures the default language model slot
- **THEN** the selector SHALL load model options from the aggregated `model-options` Console API
- **AND** the selector SHALL only present available models with `modelType = language`
- **AND** the selector SHALL display model display names with provider display context to distinguish models from different providers
- **AND** the selected value SHALL remain the `AiModel.metadata.name`

#### Scenario: Empty slot state
- **WHEN** no compatible model option exists for a default slot
- **THEN** the Console SHALL show an empty state that guides the admin to add or import a compatible model first
