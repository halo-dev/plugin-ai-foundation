## Why

The console UI has several broken interactions that prevent basic CRUD workflows: deleting providers/models silently fails, adding models does nothing, and switching providers shows stale model data. Additionally, the provider creation form requires manual displayName input even though the selected provider type already has a well-known display name.

## What Changes

- Fix provider switching: models in the detail panel must refresh when a different provider is selected
- Fix model delete: `Dialog` and `Toast` from `@halo-dev/components` are used as globals but never imported, causing `ReferenceError`
- Fix provider delete: same root cause as model delete — missing `Dialog`/`Toast` imports
- Fix manual model addition: `ProviderDetail.vue` has a `modelFormVisible` ref and "添加模型" button but no `VModal` + `ModelForm` in the template
- Auto-fill displayName: when creating a provider, selecting a provider type should auto-populate the displayName field with the type's display name

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `console-model-management`: multiple console requirements are broken — model deletion, provider deletion, model addition, provider switching, and provider creation UX all need fixes to meet their existing spec

## Impact

- `ui/src/views/ProviderDetail.vue` — add `:key`, add `VModal` + `ModelForm`, import `Dialog`/`Toast`
- `ui/src/views/ProviderManager.vue` — import `Dialog`/`Toast`
- `ui/src/views/ModelList.vue` — import `Dialog`/`Toast`
- `ui/src/views/ProviderForm.vue` — watch `providerType` to auto-fill `displayName`
