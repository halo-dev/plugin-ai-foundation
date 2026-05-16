## Why

The provider manager currently uses a fixed-width provider list beside a flexible detail pane. On mobile viewports, the fixed left pane leaves too little space for the provider detail and model list, making the main workspace effectively unusable.

## What Changes

- Make the provider configuration workspace responsive so the provider list and provider detail remain usable on mobile screens.
- Preserve the existing desktop master-detail layout with the provider list on the left and provider detail on the right.
- Adjust compact detail content, such as provider metadata and action controls, so it wraps or stacks without horizontal compression.
- UI-only change: no backend API, extension schema, provider behavior, or generated client changes.
- Non-goals:
  - Redesigning provider CRUD flows.
  - Changing provider/model data fetching behavior.
  - Introducing a new mobile navigation pattern such as drawers or a separate route.
  - Changing the all-models tab layout beyond avoiding regressions.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `console-model-management`: define responsive behavior for the provider-centric Console workspace on mobile and desktop viewports.

## Impact

- Affected UI files:
  - `ui/src/views/ProviderManager.vue`
  - `ui/src/views/ProviderDetail.vue`
  - Potentially provider/model list child components if wrapping issues are found during verification
- No backend impact.
- No dependency impact.
- Verification should cover mobile-width and desktop-width rendering of the provider configuration tab.
