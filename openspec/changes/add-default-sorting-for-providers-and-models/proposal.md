## Why

当前 Providers 和 Models 的列表接口返回的数据没有默认排序，导致前端展示顺序不稳定——新创建的供应商或模型可能出现在列表中间或末尾，用户难以快速定位最新添加的项。统一按创建时间倒序排列，可以让最新创建的项始终出现在最前面，提升使用体验。

## What Changes

- 修改 `ProviderConsoleEndpoint.listProviders()`，将 `Sort.unsorted()` 替换为按 `metadata.creationTimestamp` 倒序
- 修改 `ModelConsoleEndpoint.listModels()`，将 `Sort.unsorted()` 替换为按 `metadata.creationTimestamp` 倒序
- 修改 `AiModelServiceImpl.listProviders()`，将 `Sort.unsorted()` 替换为按 `metadata.creationTimestamp` 倒序
- 修改 `AiModelServiceImpl.listModels()`，将 `Sort.unsorted()` 替换为按 `metadata.creationTimestamp` 倒序

## Capabilities

### New Capabilities

<!-- 无新 capability -->

### Modified Capabilities

- `console-model-management`: Provider 和 Model 列表接口现在默认按创建时间倒序返回

## Impact

- **后端**: 3 个 Java 文件（`ProviderConsoleEndpoint`、`ModelConsoleEndpoint`、`AiModelServiceImpl`）
- **前端**: 无变更，前端自动获得排序后的数据
- **公开 API**: `AiModelService.listProviders()` 和 `listModels()` 的返回顺序发生变化（最新在前）
- **依赖**: 无新增
