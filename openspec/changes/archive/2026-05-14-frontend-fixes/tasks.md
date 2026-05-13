## 1. Fix Dialog/Toast imports (blocks delete & toast functionality)

- [x] 1.1 In `ModelList.vue`, import `Dialog` and `Toast` from `@halo-dev/components`, remove `// eslint-disable-next-line no-undef` comments
- [x] 1.2 In `ProviderManager.vue`, import `Dialog` and `Toast` from `@halo-dev/components`, remove `// eslint-disable-next-line no-undef` comments

## 2. Fix provider switch model refresh

- [x] 2.1 In `ProviderManager.vue`, add `:key="selectedProvider.metadata.name"` to `<ProviderDetail>`

## 3. Fix manual model addition

- [x] 3.1 In `ProviderDetail.vue`, add `<VModal>` + `<ModelForm>` controlled by the existing `modelFormVisible` ref, with proper save callback that invalidates provider-scoped model queries

## 4. Auto-fill displayName on provider type selection

- [x] 4.1 In `ProviderForm.vue`, watch `formValues.providerType` and auto-set `formValues.displayName` to `selectedProviderType.displayName` when in creation mode and displayName is empty

## 5. Verify fixes

- [x] 5.1 Start dev server and verify: switching providers refreshes model list
- [x] 5.2 Verify: model delete shows Dialog.confirm and deletes successfully
- [x] 5.3 Verify: provider delete shows Dialog.confirm and deletes successfully (with model check toast)
- [x] 5.4 Verify: "添加模型" button opens modal with form, can create model
- [x] 5.5 Verify: selecting provider type in creation form auto-fills displayName
