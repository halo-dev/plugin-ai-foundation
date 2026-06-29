# default-model-slots Specification

## Purpose
TBD - created by archiving change redesign-model-capability-architecture. Update Purpose after archive.
## Requirements
### Requirement: Default model slots
The system SHALL allow super administrators to configure central default model slots by model type.
The system SHALL persist default slots in the AI Foundation plugin Setting-backed ConfigMap, not in a dedicated default-slot Extension.

#### Scenario: Configure default language model
- **WHEN** an admin selects a default language model
- **THEN** the system SHALL persist the selected model as an `AiModel.metadata.name`
- **AND** the value SHALL be stored in the plugin ConfigMap `defaults` group
- **AND** the backend SHALL validate that the selected `AiModel` exists, is enabled, and has `modelType = language`

#### Scenario: Configure default embedding model
- **WHEN** an admin selects a default embedding model
- **THEN** the system SHALL persist the selected model as an `AiModel.metadata.name`
- **AND** the backend SHALL validate that the selected `AiModel` exists, is enabled, and has `modelType = embedding`

#### Scenario: Configure future default slots
- **WHEN** the system supports default slots for rerank or image generation
- **THEN** the backend SHALL validate that each selected model's `modelType` matches the slot type
- **AND** the stored value SHALL be an `AiModel.metadata.name`

### Requirement: Default model resolution
The system SHALL resolve default model slots for callers that do not provide an explicit model name.

#### Scenario: Resolve default language model
- **WHEN** a caller asks for the default language model
- **AND** a default language model slot is configured
- **THEN** the system SHALL resolve the configured `AiModel.metadata.name`
- **AND** return the same callable language wrapper behavior as resolving that model explicitly

#### Scenario: Missing default model
- **WHEN** a caller asks for a default model slot that is not configured
- **THEN** the system SHALL return a typed error indicating that the default model slot is not configured
- **AND** the system SHALL NOT silently choose an arbitrary model

#### Scenario: Disabled default model
- **WHEN** a default model slot references a disabled `AiModel` or a model whose provider is disabled
- **THEN** the system SHALL return the existing disabled model or disabled provider error behavior

### Requirement: Default slots are not runtime failover
Default model slots SHALL provide central model selection, not automatic retry or failover chains.

#### Scenario: Invocation failure
- **WHEN** a configured default model fails during invocation due to provider API errors, quota, or network failure
- **THEN** the system SHALL surface the invocation error to the caller
- **AND** it SHALL NOT automatically retry another configured model unless a future failover feature explicitly provides that behavior

### Requirement: Console default model settings
The Console SHALL provide a super-admin settings surface for choosing default model slots.
The settings surface SHALL be reachable through a dedicated AI Foundation child route instead of a query-tab state on a shared management page.

#### Scenario: Open default model route
- **WHEN** an admin opens the default model child route
- **THEN** the Console SHALL display the default model slot settings surface
- **AND** the active AI Foundation section SHALL be "默认模型"

#### Scenario: Slot selector filters by model type
- **WHEN** an admin configures the default language model slot
- **THEN** the selector SHALL load model options from the aggregated `model-options` Console API
- **AND** the selector SHALL only present available models with `modelType = language`
- **AND** the selector SHALL display model display names with provider display context to distinguish models from different providers
- **AND** the selected value SHALL remain the `AiModel.metadata.name`

#### Scenario: Empty slot state
- **WHEN** no compatible model option exists for a default slot
- **THEN** the Console SHALL show an empty state that guides the admin to add or import a compatible model first

### Requirement: Default reranking model slot
The system SHALL provide a default reranking model slot alongside language and embedding model slots.

#### Scenario: Configure default reranking model
- **WHEN** an administrator configures default model slots
- **THEN** they can select an enabled reranking model as the default reranking model

#### Scenario: Reranking default is optional
- **WHEN** no default reranking model is configured
- **THEN** language and embedding model defaults continue to resolve independently

### Requirement: Default image generation model slot
The system SHALL support a default image generation model slot alongside language, embedding, and rerank defaults.

#### Scenario: Configure default image generation model
- **WHEN** an administrator configures default model slots
- **THEN** they can select an enabled image generation model as the default image generation model
- **AND** the backend SHALL validate that the selected model has `modelType = image-generation`

#### Scenario: Resolve default image generation model
- **WHEN** a consumer asks for the default image generation model
- **AND** a default image generation slot is configured
- **THEN** the system SHALL resolve the configured `AiModel.metadata.name`
- **AND** it SHALL return the same callable image generation wrapper behavior as resolving that model explicitly

#### Scenario: Missing image generation default
- **WHEN** a consumer asks for the default image generation model and no slot is configured
- **THEN** the system SHALL return the existing typed missing-default error behavior
- **AND** it SHALL NOT silently choose an arbitrary image generation model

