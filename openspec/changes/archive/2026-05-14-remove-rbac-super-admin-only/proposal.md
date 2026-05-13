## Why

This plugin configures AI providers, API keys, and models — infrastructure-level settings that only super-administrators should manage. The current RBAC role templates and UI permission checks add unnecessary complexity with no real security benefit, since non-super-admins should never access these features at all.

## What Changes

- **Remove** `roleTemplate.yaml` — delete the two role templates (manage + view) entirely
- **Remove** UI permission checks — strip `plugin:ai-foundation:manage` and `plugin:ai-foundation:view` from Vue components
- **Remove** `rbac.authorization.halo.run/ui-permissions` annotations and related RBAC rule definitions
- All endpoints and resources become super-admin-only by default (Halo's built-in behavior when no role templates exist)

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `ai-provider-config`: Remove RBAC role templates and UI permission gates; access becomes super-admin-only
- `console-model-management`: Remove UI permission checks from console views; access becomes super-admin-only

## Impact

- **Backend**: Delete `roleTemplate.yaml`, remove RBAC annotations from plugin config
- **Frontend**: Remove permission directive/checks from Vue components
- **API**: No endpoint changes — Halo's default super-admin policy applies when no role templates exist
- **Users**: Non-super-admins will lose any access (expected — this is the goal)
