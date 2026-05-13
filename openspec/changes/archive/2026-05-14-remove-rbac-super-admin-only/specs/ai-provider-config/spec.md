## REMOVED Requirements

### Requirement: RBAC role templates for AI Foundation
**Reason**: The plugin is only intended for super-administrators. Halo's default behavior restricts Extension resources without role templates to super-admins, making explicit role templates unnecessary.
**Migration**: No migration needed — the plugin is unreleased. All `AiProvider` and `AiModel` resources become super-admin-only by default when `roleTemplate.yaml` is removed.
