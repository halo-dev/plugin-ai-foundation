## ADDED Requirements

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
- **THEN** the system SHALL display all `AiProvider` resources in a card or table layout
- **AND** each item SHALL show `providerType`, `displayName`, `enabled` status, and `status.phase`

### Requirement: Create new provider
The Console UI SHALL allow admins to create a new `AiProvider` Extension by selecting a provider type and filling in configuration fields.

#### Scenario: Create OpenAI provider
- **WHEN** an admin clicks "添加模型供应商"
- **AND** selects "OpenAI" as the provider type
- **AND** enters display name "OpenAI Official" and API key
- **AND** clicks save
- **THEN** the system SHALL create a new `AiProvider` Extension via POST to the Extension API
- **AND** the new provider SHALL appear in the list

### Requirement: Edit provider configuration
The Console UI SHALL allow admins to edit an existing `AiProvider`'s configuration.

#### Scenario: Update API key
- **WHEN** an admin clicks edit on an existing OpenAI provider
- **AND** changes the API key
- **AND** clicks save
- **THEN** the system SHALL update the `AiProvider` Extension via PUT to the Extension API
- **AND** subsequent AI calls SHALL use the new API key

### Requirement: Delete provider
The Console UI SHALL allow admins to delete an `AiProvider` Extension.

#### Scenario: Delete provider
- **WHEN** an admin clicks delete on a provider
- **AND** confirms the deletion
- **THEN** the system SHALL delete the `AiProvider` Extension via DELETE to the Extension API
- **AND** the provider SHALL disappear from the list

### Requirement: Model list view
The Console UI SHALL display a list of all `AiModel` Extensions, showing each model as `(Provider / Model)` format.

#### Scenario: View all models
- **WHEN** an admin navigates to the model list tab
- **THEN** the system SHALL display all `AiModel` resources
- **AND** each item SHALL show in format `(ProviderDisplayName / ModelDisplayName)`
- **AND** each item SHALL show the underlying `providerName/modelId` reference

### Requirement: Add model from provider
The Console UI SHALL allow admins to add a new `AiModel` by selecting a configured provider and specifying a model ID.

#### Scenario: Add model from provider
- **WHEN** an admin clicks "添加模型"
- **AND** selects an existing configured provider (e.g., "OpenAI Official")
- **AND** enters model ID (e.g., "gpt-4o") and display name (e.g., "GPT-4o")
- **AND** clicks save
- **THEN** the system SHALL create a new `AiModel` Extension via POST to the Extension API
- **AND** the new model SHALL appear in the list as `(OpenAI Official / GPT-4o)`

### Requirement: Browse provider models
The Console UI SHALL allow browsing available models from a provider's API to simplify model addition.

#### Scenario: Browse and add from provider API
- **WHEN** an admin selects a provider and clicks "从供应商获取模型列表"
- **THEN** the system SHALL call the model listing endpoint for that provider
- **AND** display available models
- **AND** allow the admin to select one or more models to add as `AiModel` entries

### Requirement: Edit model
The Console UI SHALL allow admins to edit an existing `AiModel`'s display name.

#### Scenario: Update model display name
- **WHEN** an admin clicks edit on a model
- **AND** changes the display name
- **AND** clicks save
- **THEN** the system SHALL update the `AiModel` Extension via PUT to the Extension API

### Requirement: Delete model
The Console UI SHALL allow admins to delete an `AiModel` Extension.

#### Scenario: Delete model
- **WHEN** an admin clicks delete on a model
- **AND** confirms the deletion
- **THEN** the system SHALL delete the `AiModel` Extension via DELETE to the Extension API
- **AND** the model SHALL disappear from the list

### Requirement: Connectivity testing
The Console UI SHALL provide a manual connectivity test button for each provider.

#### Scenario: Test OpenAI connectivity
- **WHEN** an admin clicks "测试连通性" on an OpenAI provider
- **THEN** the system SHALL call the connectivity test endpoint
- **AND** display success if the API key is valid
- **AND** display an error message if the API key is invalid or the service is unreachable

### Requirement: RBAC permissions
The plugin SHALL define role templates for viewing and managing AI providers and models.

#### Scenario: Permission enforcement
- **WHEN** a non-admin user tries to access the AI model configuration page
- **THEN** the system SHALL deny access based on RBAC rules
- **AND** the menu item SHALL be hidden if the user lacks permission
