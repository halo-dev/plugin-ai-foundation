## REMOVED Requirements

### Requirement: RBAC permissions
**Reason**: The plugin is only intended for super-administrators. Removing the `permissions` meta field from the frontend route relies on Halo's default super-admin-only access when no role templates exist.
**Migration**: No migration needed — the plugin is unreleased. The menu item and route will only be visible to super-admins by default.

#### Scenario: Super-admin only access
- **WHEN** a non-super-admin user opens the Halo Console
- **THEN** the "AI 模型配置" menu item SHALL NOT be visible
- **AND** direct navigation to `/ai-foundation` SHALL be denied by Halo's default access control
