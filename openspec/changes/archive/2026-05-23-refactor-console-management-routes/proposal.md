## Why

The AI Foundation Console currently uses a single `ProviderManager` page with a `tab` query parameter and `v-if` branches to switch between provider configuration, all-model browsing, default model slots, and the model test workbench. As these sections gain their own query parameters and state, keeping them behind one pseudo-tab route makes navigation, deep links, and future state ownership harder to reason about.

## What Changes

- Replace the `tab` query-driven Console section switcher with dedicated child routes under the existing AI Foundation Console entry.
- Keep the current menu entry and page title, but make the parent view a route layout that renders the selected child route.
- Preserve the existing user-facing sections:
  - `配置` for provider configuration and provider-scoped models
  - `模型列表` for all configured models
  - `默认模型` for default slot settings
  - `测试` for the chat-style model test workbench
- Move model-row test actions from `?tab=test&model=...` to the test child route with `?model=...`.
- Keep existing backend APIs and generated clients unchanged.
- Non-goals:
  - No backend API or Java SDK changes.
  - No permission model changes.
  - No redesign of provider/model CRUD behavior.
  - No migration of persisted provider/model/default-slot data.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `console-model-management`: Console navigation and model-management entry points SHALL use dedicated child routes instead of a single query-tab view.
- `default-model-slots`: The default model slot settings surface SHALL be reachable through its own Console child route.
- `model-test-workbench`: The model test workbench SHALL be reachable through its own Console child route, and model-row test actions SHALL navigate there with the selected model query.

## Impact

- UI-only change.
- Affected UI areas:
  - `ui/src/index.ts` route registration
  - `ui/src/views/ProviderManager.vue` or its replacement layout
  - provider configuration, all-model list, default model slot, and model test view composition
  - model-row actions that open the test workbench
- Affected specs:
  - `openspec/specs/console-model-management/spec.md`
  - `openspec/specs/default-model-slots/spec.md`
  - `openspec/specs/model-test-workbench/spec.md`
- Expected verification:
  - `pnpm -C ui type-check`
  - targeted unit tests if route helpers or navigation behavior are covered
  - `openspec validate refactor-console-management-routes --strict`
