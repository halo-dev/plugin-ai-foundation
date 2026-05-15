## 1. Dependencies

- [x] 1.1 Add `fuse.js` and `@vueuse/integrations` to `ui/package.json`
- [x] 1.2 Run `pnpm install` in `ui/` directory to update lockfile

## 2. UI Implementation

- [x] 2.1 Import `useFuse` from `@vueuse/integrations/useFuse` in `ModelsDiscoveryModal.vue`
- [x] 2.2 Add `keyword` ref and wire up `useFuse` with `displayName` + `modelId` keys and threshold 0.3
- [x] 2.3 Add search input box at the top of modal body using `SearchInput` component
- [x] 2.4 Replace `v-for="model in models?.models"` with filtered results
- [x] 2.5 Add empty state for no search results ("未找到匹配的模型")
- [x] 2.6 Verify selected models are still imported correctly when filtered out by search
- [x] 2.7 Run `pnpm type-check` to ensure TypeScript compiles
