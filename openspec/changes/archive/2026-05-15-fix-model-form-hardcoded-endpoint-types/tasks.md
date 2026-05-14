## 1. ModelForm.vue — 动态 endpoint type 选项

- [x] 1.1 新增 `providerType: string` prop
- [x] 1.2 使用 `useProviderType` composable 获取当前 provider type 的 `supportedEndpointTypes`
- [x] 1.3 根据 `supportedEndpointTypes` 动态生成 `endpointTypeOptions`，为每个值提供可读的 label
- [x] 1.4 移除硬编码的 `endpointTypeOptions` 数组
- [x] 1.5 当 `supportedEndpointTypes` 为空或仅有一项时，设置合适的默认值并处理边界情况

## 2. ModelCreationModal.vue — 传入 providerType

- [x] 2.1 改为接收 `provider: AiProvider` prop（替换现有的 `providerName: string`）
- [x] 2.2 从 `provider.metadata.name` 和 `provider.spec.providerType` 分别提取 `providerName` 和 `providerType`
- [x] 2.3 将 `providerType` 传给 `ModelForm` 组件
- [x] 2.4 更新 `ProviderModelList.vue` 中 `ModelCreationModal` 的传参，传入 `provider` 对象

## 3. ModelEditingModal.vue — 查找并传入 providerType

- [x] 3.1 使用 `useProvidersFetch()` 获取 provider 列表
- [x] 3.2 用 `computed` 从 `model.spec.providerName` 查找对应的 `provider`
- [x] 3.3 将 `provider?.spec.providerType` 传给 `ModelForm` 组件

## 4. 验证

- [x] 4.1 运行 `cd ui && pnpm type-check` 确保类型正确
- [x] 4.2 确认各 provider 的 endpoint type 下拉选项与后端 `supportedEndpointTypes` 一致
- [x] 4.3 更新 `plan/code-review-2026-05-14.md`，将第 6 条标记为已修复
