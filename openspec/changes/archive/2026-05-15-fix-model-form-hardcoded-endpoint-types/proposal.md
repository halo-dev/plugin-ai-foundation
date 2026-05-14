## Why

`ModelForm.vue` 硬编码了 `endpointTypeOptions`，与项目规范"Frontend must not hardcode provider type lists"冲突。用户可以为 Ollama provider 选择 `openai-embedding` 这种不合法的组合。后端已通过 `ProviderTypeInfo.supportedEndpointTypes` 暴露支持的 endpoint types，但前端没有使用。

## What Changes

- `ModelForm.vue`：移除硬编码 `endpointTypeOptions`，改为接收 provider type 的 `supportedEndpointTypes` 动态生成选项
- `ModelCreationModal.vue`：通过父组件传入当前 provider 的 `providerType`，供 `ModelForm` 查询对应的 `supportedEndpointTypes`
- `ModelEditingModal.vue`：通过父组件传入当前 model 关联的 provider 的 `providerType`

## Capabilities

### New Capabilities
- (none — this is a UI-only fix, no new behavior)

### Modified Capabilities
- (none — no spec-level requirement changes)

## Impact

- 仅影响前端 UI 组件：`ui/src/views/components/ModelForm.vue`、`ModelCreationModal.vue`、`ModelEditingModal.vue`
- 无后端 API 变更
- 无数据迁移需求
