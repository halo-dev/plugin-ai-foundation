## Context

The Console UI currently registers one route at `/ai-foundation` and renders `ProviderManager.vue`. That view owns the page header, tab navigation, and all four major sections:

- provider configuration and provider-scoped models
- all configured models
- default model slots
- model test workbench

The active section is stored in the `tab` query parameter. Other state is already section-specific: provider configuration uses `provider`, and the test workbench uses `model`. This creates mixed query ownership on a single route and encourages route-level state to leak across unrelated sections.

This change is UI-only. It should preserve existing backend APIs, generated API clients, model/provider data contracts, and super-admin-only usage.

## Goals / Non-Goals

**Goals:**

- Replace `tab` query switching with dedicated child routes under the AI Foundation Console route.
- Keep the existing menu entry and visible page shell stable.
- Make each major Console section a route-level view so it can own its own query parameters and component state.
- Preserve deep links for selected provider and selected test model using route-specific query parameters.
- Keep route-level view components thin and push feature UI into existing focused components.

**Non-Goals:**

- No backend API, Java API, generated client, or database/config migration.
- No provider/model CRUD redesign.
- No role or permission redesign.
- No compatibility redirect from `?tab=...`; the plugin is unreleased and does not need backward-compatible URL handling.
- No conversion of provider selection from `?provider=...` to a path parameter in this change.

## Decisions

### Use an AI Foundation route layout with child routes

The existing `/ai-foundation` route should become a layout route that renders the page header, route-aware section navigation, and a child `<RouterView />`.

Proposed route shape:

```text
/ai-foundation
  redirect: /ai-foundation/providers

/ai-foundation/providers
  label: 配置
  query: provider=<AiProvider.metadata.name>

/ai-foundation/models
  label: 模型列表
  query: optional list filters when implemented

/ai-foundation/defaults
  label: 默认模型

/ai-foundation/test
  label: 测试
  query: model=<AiModel.metadata.name>
```

Alternative considered: keep `/ai-foundation?tab=...` and move each section into smaller components. That would improve component size but would not solve route ownership or deep-link clarity.

### Keep provider and test selections as route-specific query parameters

The provider workspace should continue using `?provider=...` because it is a master-detail selection inside the provider configuration route. The test workbench should continue using `?model=...`, but only on the test route.

Alternative considered: use `/ai-foundation/providers/:providerName` and `/ai-foundation/test/:modelName`. That gives more structured URLs, but it would increase routing surface and requires extra empty/default child states. Query parameters are sufficient for these optional selections.

### Route navigation should not carry unrelated query state by default

Switching between sections through the section navigation should navigate to the target route without copying unrelated query parameters. For example, the selected provider query should not appear on the test route unless explicitly needed, and selected test model should not appear on the default-slots route.

Alternative considered: preserve the full query object across section switches. That can make accidental state retention look convenient, but it keeps unrelated concerns coupled and recreates the current mixed-query problem.

### Model-row test actions target the test route

Model-row actions that currently set `tab=test` and `model=<name>` should navigate to the test child route with `model=<name>`. This preserves the user workflow while removing the global tab query.

Alternative considered: emit an event up to the route layout and let the layout choose the route. Direct route navigation from the action is simpler here because the action is already route-aware and represents an explicit navigation.

### Route views own single-section surfaces directly

The route-level views should avoid pass-through wrapper components. A route view that only renders one existing component should absorb that component's implementation so the route file itself owns that section.

The provider configuration route remains a composition surface because it combines two independent areas:

- provider configuration route composes `ProviderList` and `ProviderDetail`
- all-model route owns the all-model list implementation directly
- default route owns the default model slots implementation directly
- test route owns the model test workbench implementation directly

This keeps the parent route layout focused on shell/navigation while avoiding one-line view wrappers for sections that do not need a separate component boundary.

## Risks / Trade-offs

- Existing bookmarked `?tab=...` URLs stop selecting the intended section -> acceptable because the plugin is unreleased; `/ai-foundation` redirects to the provider configuration route.
- The current segmented tab component is `v-model` based -> either adapt it to route navigation through a small route-aware wrapper or replace usage with route links while keeping the same visual style.
- Query-state helpers may still be imported from components that no longer need them -> implementation should remove `tab` query helpers and keep only route-specific query helpers.
- Halo plugin route registration may require child routes to be declared directly under the appended route -> verify with `pnpm -C ui type-check` and runtime smoke testing when applying.

## Migration Plan

1. Introduce the parent route layout and child route registrations.
2. Extract the provider configuration section from the current manager view into a route-level view.
3. Move all-model, default-slot, and test workbench rendering behind child routes.
4. Replace all `tab` query writes with named-route or path-based navigation to the correct child route.
5. Validate type checking and OpenSpec.

Rollback is straightforward: restore the single `ProviderManager.vue` route and the `tab` query switcher. No persisted data changes are involved.

## Open Questions

- Should list filters on `/ai-foundation/models` become query parameters in the same implementation, or remain component-local state until a separate filtering/deep-linking change?
- Should the existing `SegmentedTabs` component be generalized to support route links, or should this page own a small route-aware navigation component?
