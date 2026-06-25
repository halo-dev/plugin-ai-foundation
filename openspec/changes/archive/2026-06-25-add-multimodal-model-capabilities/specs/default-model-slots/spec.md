## ADDED Requirements

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
