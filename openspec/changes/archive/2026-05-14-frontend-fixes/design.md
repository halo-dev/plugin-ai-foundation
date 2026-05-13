## Context

The console UI (`ui/src/views/`) has five broken interactions discovered during browser testing. All are bugs in existing code — no new features needed. The root causes are: missing component imports (`Dialog`/`Toast` from `@halo-dev/components` used as globals), a missing template section (no `VModal`+`ModelForm` for adding models), a stale query key on provider switch, and a missing auto-fill UX enhancement.

## Goals / Non-Goals

**Goals:**
- Fix all 5 broken interactions so basic CRUD workflows work end-to-end
- Ensure `Dialog` and `Toast` are properly imported wherever used

**Non-Goals:**
- Refactoring the UI component structure or adding new features
- Changing the backend API or data model
- Improving the design/visual appearance

## Decisions

### 1. Provider switch: `:key` on ProviderDetail

**Decision**: Add `:key="selectedProvider.metadata.name"` to `<ProviderDetail>` in `ProviderManager.vue`.

**Alternative considered**: Make `useModelsByProvider` accept a reactive ref so the query key updates reactively. This is more complex and over-engineered for a simple component-replacement case. The `:key` approach is standard Vue pattern and forces a clean re-mount with fresh state.

### 2. Dialog/Toast: Import from @halo-dev/components

**Decision**: Import `Dialog` and `Toast` from `@halo-dev/components` in `ProviderManager.vue` and `ModelList.vue`, remove the `// eslint-disable-next-line no-undef` comments.

**Rationale**: Both are confirmed exports of `@halo-dev/components`. The code was written assuming they were globals (hence the eslint-disable), but they are not — causing `ReferenceError` at runtime.

### 3. Manual model addition: Add VModal + ModelForm to ProviderDetail

**Decision**: Add a `<VModal>` with `<ModelForm>` inside `ProviderDetail.vue`, controlled by the existing `modelFormVisible` ref. On save, invalidate the provider-scoped model query.

**Alternative considered**: Move the "add model" button and modal into `ModelList.vue` so it's co-located with model CRUD. Rejected because `ModelList` is a pure presentation component receiving models as props — adding mutation logic would break its responsibility boundary.

### 4. DisplayName auto-fill: Watch providerType in creation mode

**Decision**: In `ProviderForm.vue`, watch `formValues.providerType` and when it changes during creation (not editing), set `formValues.displayName` to `selectedProviderType.displayName` only if displayName is currently empty.

**Alternative considered**: Make displayName optional, fill server-side on create. Rejected — it's simpler to do client-side and the user can always override the auto-filled value. Also avoids backend changes.

## Risks / Trade-offs

- **[:key causes full re-mount]** → Acceptable: ProviderDetail is lightweight, and re-mount guarantees fresh state. The only cost is losing ephemeral UI state (e.g. search filter text in ModelList), which is actually desirable when switching providers.
- **[Auto-fill overwrites user input]** → Mitigated by only auto-filling when displayName is empty. If user has already typed something, it won't be overwritten.
