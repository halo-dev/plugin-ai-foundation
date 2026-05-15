## Why

模型发现弹窗在供应商返回大量模型时（如 OpenAI、Ollama 等），用户需要滚动很长的列表才能找到目标模型。一个前端搜索功能可以显著改善这个体验，让用户通过输入关键词快速定位模型。

## What Changes

- 在 `ModelsDiscoveryModal.vue` 弹窗顶部添加搜索输入框，使用全局注册的 `SearchInput` 组件
- 引入 `fuse.js` + `@vueuse/integrations/useFuse` 实现模糊搜索
- 搜索字段覆盖 `displayName` 和 `modelId`，Fuse 阈值设为 0.3
- 搜索无结果时显示独立的空状态提示（与"无法获取模型列表"区分）
- 已选模型的状态不受搜索过滤影响，导入时仍从原始完整列表中读取

## Capabilities

### New Capabilities

- `model-discovery-search`: 模型发现弹窗的前端搜索过滤能力

### Modified Capabilities

<!-- 无现有 spec 需要修改 — 纯 UI 增强，不涉及 spec-level 行为变更 -->

## Impact

- **UI**: `ui/src/views/components/ModelsDiscoveryModal.vue`
- **依赖**: 新增 `fuse.js` 和 `@vueuse/integrations` 到 `ui/package.json`
- **后端**: 无变更
