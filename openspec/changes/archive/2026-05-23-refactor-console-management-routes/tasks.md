## 1. Route Structure

- [x] 1.1 Update `ui/src/index.ts` so `/ai-foundation` is a parent route that redirects to the provider configuration child route.
- [x] 1.2 Add child routes for provider configuration, all-model list, default model slots, and model test workbench.
- [x] 1.3 Keep existing route meta for menu registration, permissions, title, icon, and hidden footer behavior on the AI Foundation route surface.

## 2. Route-Level Views

- [x] 2.1 Convert the current `ProviderManager.vue` responsibility into a thin AI Foundation route layout with page header, route-aware section navigation, and child route rendering.
- [x] 2.2 Create or extract a provider configuration route view that composes `ProviderList` and `ProviderDetail` with the existing responsive master-detail layout.
- [x] 2.3 Render the all-model list, default model slots panel, and model test workbench from their own child route views.

## 3. Navigation And Query State

- [x] 3.1 Replace `tab` query state in the section navigation with child-route navigation and active state derived from the current route.
- [x] 3.2 Preserve provider selection as `provider` query state only on the provider configuration route.
- [x] 3.3 Preserve selected test model as `model` query state only on the test route.
- [x] 3.4 Update model-row test actions to navigate to the test child route with `model=<AiModel.metadata.name>`.
- [x] 3.5 Update all-model empty-state actions to navigate to the provider configuration child route instead of writing `tab=config`.

## 4. Cleanup And Verification

- [x] 4.1 Remove obsolete `tab` query imports and state writes from AI Foundation UI components.
- [x] 4.2 Run `pnpm -C ui type-check`.
- [x] 4.3 Run focused UI tests if route helpers or navigation behavior are covered by tests.
- [x] 4.4 Run `openspec validate refactor-console-management-routes --strict`.
- [x] 4.5 Smoke-check the Console routes when a dev server is available: provider configuration, all-model list, default model slots, and test workbench.
