## Context

`ProviderManager.vue` currently renders the provider configuration tab as a horizontal flex workspace. The provider list uses a fixed `w-72 flex-none` column, and the provider detail uses the remaining width. This works on desktop but leaves the detail pane nearly invisible on mobile viewports.

The page is a Halo Console plugin UI built with Vue 3, UnoCSS utility classes, and Halo shared components. The change is UI-only and should preserve the existing route, tabs, provider selection query state, and data fetching behavior.

## Goals / Non-Goals

**Goals:**

- Keep the desktop provider manager as a provider-centric master-detail workspace.
- Make the configuration tab usable on mobile screens by preventing the provider list from consuming fixed horizontal space.
- Keep provider list, provider detail, and provider model list visible and scrollable on compact screens.
- Ensure detail header actions and metadata fields wrap or stack instead of causing horizontal overflow.

**Non-Goals:**

- Replace the master-detail model with separate routes, drawers, or modal navigation.
- Change provider/model API calls, query keys, generated clients, or backend behavior.
- Redesign provider and model CRUD forms.
- Add new frontend dependencies.

## Decisions

### Decision 1: Stack the workspace on small screens, keep split panes on larger screens

Use responsive layout utilities so mobile viewports render the provider list above the detail pane, then switch back to the existing left-list/right-detail layout at a tablet or desktop breakpoint.

Alternative considered: hide the provider list on mobile and add a separate selector. This gives the detail pane more space, but it introduces a new interaction model and makes provider creation less direct. Stacking is smaller, clearer, and consistent with the existing workspace.

Alternative considered: keep the two-column layout and reduce the provider list width. This still leaves too little room on narrow phones and makes both panes cramped.

### Decision 2: Give the mobile provider list a bounded height

The provider list should not push the detail pane below an unusably long page when many providers exist. On mobile, the list should occupy a bounded top region and scroll internally; on larger screens it can continue to fill the workspace height.

Alternative considered: let the provider list height be natural on mobile. This is simple, but a long provider list would bury the selected provider details and model list.

### Decision 3: Make detail content resilient to narrow widths

Provider detail actions and metadata should wrap or stack on compact screens. The metadata grid should use one column on the narrowest screens and progressively increase columns as space allows.

Alternative considered: leave detail internals unchanged after stacking the outer layout. This fixes the main compression issue but still risks button overflow and long metadata values crowding adjacent fields.

## Risks / Trade-offs

- [Risk] A bounded mobile provider list may require users with many providers to scroll the list separately from the detail pane. -> Mitigation: keep the list at the top, preserve visible selection state, and ensure the detail pane remains accessible immediately below it.
- [Risk] Different Halo Console shell widths may make breakpoint choice feel slightly early or late. -> Mitigation: verify at representative mobile and desktop widths and use standard UnoCSS breakpoints rather than hard-coded media queries.
- [Risk] Long provider resource names or model IDs may still overflow inside Halo shared components. -> Mitigation: inspect the detail metadata and model list after layout changes and add truncation or wrapping only where needed.
