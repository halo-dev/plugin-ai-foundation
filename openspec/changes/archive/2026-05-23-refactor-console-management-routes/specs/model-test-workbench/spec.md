## REMOVED Requirements

### Requirement: ProviderManager exposes a model test workbench
**Reason**: The workbench is no longer exposed as a `ProviderManager` tab; it is exposed as an AI Foundation child route so it can own its route and query state independently.
**Migration**: Use the dedicated test child route and its `model` query parameter to open the workbench with a selected model.

## ADDED Requirements

### Requirement: Console test route exposes a model test workbench
The system SHALL provide a dedicated AI Foundation "测试" child route for testing configured chat models.

#### Scenario: User opens the test route
- **WHEN** a super administrator opens the AI Foundation test child route
- **THEN** the system displays a chat-style model testing workbench
- **AND** the workbench includes a conversation area, a message input area, a model selector, and a parameter configuration area
- **AND** the active AI Foundation section SHALL be "测试"

#### Scenario: Test route selects model from query
- **WHEN** a super administrator opens the test child route with `model={name}` in the query
- **THEN** the selected model in the workbench SHALL be set to the model whose `AiModel.metadata.name` equals `{name}`

#### Scenario: Model row test action opens workbench
- **WHEN** a super administrator chooses the test action for a chat-capable model from a model list
- **THEN** the system SHALL navigate to the AI Foundation test child route
- **AND** the route SHALL include `model={name}` for the selected model
- **AND** the selected model in the workbench SHALL be set to that model
