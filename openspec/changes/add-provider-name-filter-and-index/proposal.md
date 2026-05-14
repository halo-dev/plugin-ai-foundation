## Why

当前 `ModelConsoleEndpoint.listModels()` 返回全部 AI 模型，前端 `useModelsByProvider` 在客户端过滤，导致不必要的全量数据传输。同时 `ProviderConsoleEndpoint.deleteProvider()` 使用 predicate 全表扫描检查关联模型，性能差。为 `AiModel.spec.providerName` 添加索引并在服务端支持筛选，可以显著优化这两个场景。

## What Changes

- 为 `AiModel` 注册 `spec.providerName` 单值索引
- `GET /models` 接口支持通用 `fieldSelector`（和 `labelSelector`）查询参数，前端可传 `fieldSelector=spec.providerName=xxx` 筛选
- `ProviderConsoleEndpoint.deleteProvider()` 改用索引查询替代 predicate 全表扫描
- 前端 `useModelsByProvider` 改为传 `fieldSelector` 查询参数，移除客户端过滤
- 重新生成 TypeScript API client

## Capabilities

### New Capabilities

- `model-list-filtering`: Model 列表接口支持 fieldSelector/labelSelector 筛选

### Modified Capabilities

- *(none — 现有 API 行为不变，仅新增可选查询参数)*

## Impact

- **后端**: `AiFoundationPlugin.java`, `ModelConsoleEndpoint.java`, `ProviderConsoleEndpoint.java`
- **前端**: `ui/src/composables/useModels.ts`
- **生成代码**: `ui/src/api/generated/`（需运行 `./gradlew generateApiClient`）
- **无破坏性变更**: `fieldSelector`/`labelSelector` 为可选参数，不传时返回全部模型
