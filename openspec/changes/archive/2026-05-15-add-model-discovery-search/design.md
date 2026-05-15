## Context

`ModelsDiscoveryModal.vue` 通过 `useDiscoverModelsFetch` 一次性拉取供应商的全部可发现模型，直接渲染为 `VEntity` 列表。当供应商返回数十甚至上百个模型时（如 Ollama 本地部署、OpenAI 全量模型列表），用户必须手动滚动查找目标模型，体验较差。

这是一个纯前端增强，不涉及后端 API 变更或数据模型调整。

## Goals / Non-Goals

**Goals:**
- 在模型发现弹窗内提供实时前端搜索过滤
- 支持 `displayName` 和 `modelId` 的模糊匹配
- 搜索无结果时提供清晰的空状态反馈
- 保持现有选择/导入行为不变

**Non-Goals:**
- 后端分页或过滤 API
- 搜索历史、最近搜索等高级功能
- 搜索结果排序（保持原始发现顺序）
- 搜索关键词持久化

## Decisions

### 使用 fuse.js + @vueuse/integrations/useFuse
- **Rationale**: 用户明确建议使用此方案。fuse.js 提供模糊搜索能力，支持拼写容错（如 `gpt-4o` 匹配 `gpt-4` 的近似输入），比简单的 `String.includes` 体验更好。`useFuse` 将 Fuse 实例包装为 Vue 响应式 composable，与现有 `ref`/`computed` 模式无缝衔接。
- **Alternative considered**: 仅用 `@vueuse/core` 的 `useDebounceRef` + `Array.filter` — 实现更简单，但无模糊匹配能力，用户拼写稍有偏差就找不到结果。

### Fuse 阈值设为 0.3
- **Rationale**: 默认阈值 0.6 过于宽松，会导致大量不相关结果（如搜 `gpt-4` 时 `gpt-3.5` 也可能匹配）。0.3 在精确性和容错性之间取得平衡：允许轻微拼写差异，但不引入明显无关项。

### 搜索框使用原生 `<input>` 而非 FormKit
- **Rationale**: `SearchInput` 是 Halo 全局注册的组件，项目表单场景使用 FormKit，但搜索框不属于表单提交上下文，使用轻量原生输入更合理。参考 `TestChatModal.vue` 中已有的原生 `<textarea>` 实践。

### 已选模型不受搜索过滤影响
- **Rationale**: `selectedModels` 是 `Set<modelId>`，导入时从原始完整列表过滤。如果搜索过滤后隐藏了已选模型，用户可能误以为被取消选择。保持原始行为最符合用户心智模型。

## Risks / Trade-offs

- **[Risk]** 新增两个 npm 依赖（`fuse.js`、`@vueuse/integrations`） → **Mitigation**: 两者体积都很小（fuse.js ~10KB gzipped），且 `@vueuse/integrations` 是 tree-shakeable 的。
- **[Risk]** Fuse 对中文分词支持有限 → **Mitigation**: 模型 ID 和显示名称通常为英文或中英文混合短词，Fuse 的字符级匹配足以应对。若未来有纯中文长名称搜索需求，可再评估。
