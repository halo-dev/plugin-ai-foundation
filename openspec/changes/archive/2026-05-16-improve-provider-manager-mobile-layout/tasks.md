## 1. Responsive Workspace Layout

- [x] 1.1 Update `ProviderManager.vue` so the provider configuration workspace stacks vertically on mobile viewports and switches back to a left-list/right-detail layout on larger viewports.
- [x] 1.2 Replace the mobile fixed-width provider list behavior with a full-width, bounded-height provider list that scrolls internally when there are many providers.
- [x] 1.3 Keep the desktop provider list width and detail-pane scroll behavior equivalent to the current master-detail experience.

## 2. Compact Detail Content

- [x] 2.1 Update `ProviderDetail.vue` action controls so connectivity check, edit, and delete actions wrap or stack cleanly on narrow viewports.
- [x] 2.2 Update provider metadata layout so it uses one column on narrow viewports and progressively expands on wider viewports.
- [x] 2.3 Inspect provider model list rows for mobile overflow and add wrapping or truncation only where needed.

## 3. Verification

- [x] 3.1 Run UI type-check/build verification for the `ui` module.
- [x] 3.2 Verify the provider configuration tab at a mobile viewport width, confirming the provider list no longer compresses the detail workspace.
- [x] 3.3 Verify the provider configuration tab at a desktop viewport width, confirming the original left-list/right-detail layout is preserved.
- [x] 3.4 Verify the all-models tab still renders without layout regressions.
