## MODIFIED Requirements

### Requirement: Console navigation menu
The plugin SHALL register a Console route under the system menu group for managing AI providers and models.
The route SHALL expose dedicated child routes for the major AI Foundation management sections instead of using a single query parameter to switch sections.

#### Scenario: Menu registration
- **WHEN** an admin user opens the Halo Console
- **THEN** there SHALL be a menu item named "AI 模型配置" under the system group
- **AND** clicking it SHALL navigate to the AI Foundation management route
- **AND** the management route SHALL open the provider configuration child route by default

#### Scenario: Navigate management sections
- **WHEN** an admin uses the AI Foundation section navigation
- **THEN** the Console SHALL navigate between dedicated child routes for "配置", "模型列表", "默认模型", and "测试"
- **AND** the active section SHALL be derived from the current child route
- **AND** the active section SHALL NOT be derived from a `tab` query parameter

#### Scenario: Provider configuration route preserves provider selection
- **WHEN** an admin opens the provider configuration child route with `provider={name}` in the query
- **THEN** the provider workspace SHALL select the provider whose `metadata.name` equals `{name}`
- **AND** selecting another provider SHALL update the provider configuration route's provider query

#### Scenario: Section navigation does not leak unrelated query state
- **WHEN** an admin navigates from one AI Foundation child route to another through section navigation
- **THEN** query parameters that belong only to the previous section SHALL NOT be copied to the target section

## ADDED Requirements

### Requirement: All-model route navigation
The Console UI SHALL expose the all-model list through a dedicated AI Foundation child route.

#### Scenario: Open all-model route
- **WHEN** an admin opens the all-model child route
- **THEN** the Console SHALL display all configured `AiModel` entries
- **AND** the model list SHALL keep the existing keyword, model type, and feature filtering behavior

#### Scenario: Empty all-model route links to provider configuration
- **WHEN** the all-model child route has no matching models
- **THEN** the empty state SHALL offer an action that navigates to the provider configuration child route
