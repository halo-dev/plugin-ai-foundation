## Context

The plugin currently ships two RBAC role templates (`role-template-ai-foundation-manage` and `role-template-ai-foundation-view`) in `roleTemplate.yaml`, and the frontend route requires `plugin:ai-foundation:manage` permission. Since this plugin manages AI infrastructure (providers, API keys, models), only super-administrators should ever configure it. Halo's default behavior already restricts Extension resources without role templates to super-admins.

## Goals / Non-Goals

**Goals:**
- Remove all RBAC role templates and UI permission checks
- Rely on Halo's built-in super-admin-only access for Extension resources without role templates

**Non-Goals:**
- Adding any new permission model or access control mechanism
- Changing the API surface or endpoint behavior
- Supporting non-super-admin access in the future

## Decisions

### 1. Delete roleTemplate.yaml entirely rather than emptying it

When no role templates exist, Halo treats all Extension resources defined by the plugin as super-admin-only by default. Removing the file is cleaner than maintaining an empty or minimal template.

**Alternative considered**: Keep a minimal role template that only super-admins can assign — rejected because it adds maintenance burden with no security benefit over the default behavior.

### 2. Remove `permissions` from the frontend route meta

The `permissions: ['plugin:ai-foundation:manage']` check in `ui/src/index.ts` controls menu visibility and route access. Without it, Halo's default behavior shows the menu and route only to super-admins (since no role template grants access to non-super-admins).

**Alternative considered**: Replace with a super-admin check directive — rejected because Halo already enforces this when no role templates exist. The route will simply be visible to super-admins only.

## Risks / Trade-offs

- [Non-super-admins lose all access] → This is the intended behavior. If a future use case requires non-super-admin access, role templates can be re-added.
- [Existing installations with role templates assigned] → This plugin is unreleased, so no migration path is needed.
