## Context

`ModelForm.vue` 当前在组件内部硬编码了 `endpointTypeOptions`：

```ts
const endpointTypeOptions = [
  { value: 'openai-chat', label: 'OpenAI Chat' },
  { value: 'openai-embedding', label: 'OpenAI Embedding' },
  { value: 'ollama-chat', label: 'Ollama Chat' },
]
```

这违反了"Frontend must not hardcode provider type lists"的项目规范。后端 `ProviderTypeConsoleEndpoint` 已通过 `ProviderTypeInfo.supportedEndpointTypes` 暴露每个 provider type 支持的 endpoint types。

## Goals / Non-Goals

**Goals:**
- `ModelForm.vue` 的 endpoint type 下拉选项从 provider type 的 `supportedEndpointTypes` 动态生成
- 移除前端硬编码列表
- 确保创建和编辑模型时都使用正确的选项列表

**Non-Goals:**
- 后端 API 变更
- 新增 provider type 校验逻辑（后端已有）
- 改变模型保存/提交逻辑

## Decisions

### 1. 使用 `useProviderType` composable 获取当前 provider 的 supportedEndpointTypes

`useProviderType` 已存在，接收一个 `Ref<AiProvider>` 返回对应的 `ProviderTypeInfo`。ModelCreationModal 已持有 `providerName`，但缺少 provider type。方案：将 provider 的 `providerType` 传入 `ModelForm`，Form 内部使用 `useProviderType` 查找并生成选项。

替代方案：在父组件中查询并传入 `supportedEndpointTypes` 数组。但这样会在两个父组件中重复查询逻辑，且已有 `useProviderType` 可复用。

### 2. ModelForm 接收 `providerType: string` prop

- `ModelCreationModal` 从外部传入 provider 的 `spec.providerType`
- `ModelEditingModal` 从 model 的 `spec.providerName` 反向查 provider，再取 `spec.providerType`

实际上更简单：直接传 `providerType` 字符串即可。

- `ModelCreationModal` 通过父组件已知的 provider 数据传入 `providerType`
- `ModelEditingModal` 通过 `useProvider` composable 从 `model.spec.providerName` 查找 provider，再取 `providerType`

## Risks / Trade-offs

- **[Risk]** `supportedEndpointTypes` 返回的 endpoint type 值没有人类可读的 label（如 `openai-chat`），下拉选项可能只显示原始值。
  - **Mitigation**: 前端做简单的 label 映射（仅用于显示，不用于值），保留现有语言不变。
