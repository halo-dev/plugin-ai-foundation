## Context

当前 `ModelConsoleEndpoint.listModels()` 使用 `client.listAll(AiModel.class, new ListOptions(), Sort.unsorted())` 返回全部模型，无服务端筛选能力。前端 `useModelsByProvider` 调用该接口后在客户端用 `Array.filter()` 过滤，造成不必要的网络传输和内存开销。

`ProviderConsoleEndpoint.deleteProvider()` 使用 `client.list(AiModel.class, predicate, null)` 检查关联模型，该方式为全表扫描，在模型数量增长时性能下降。

Halo Extension 系统自 2.22 起支持索引（`IndexSpecs`）和字段选择器（`FieldSelector`），可通过 `spec.xxx` 路径对扩展字段建索引并做等值查询。

## Goals / Non-Goals

**Goals:**
- 为 `AiModel.spec.providerName` 建立单值索引
- `GET /apis/console.api.aifoundation.halo.run/v1alpha1/models` 支持 `?fieldSelector=` 和 `?labelSelector=` 查询参数（与 Halo 自动生成 CRUD API 一致）
- 将前端客户端过滤迁移至服务端索引查询
- `deleteProvider` 的关联检查改为走索引查询

**Non-Goals:**
- 支持 fieldSelector 语法本身（已实现，只需注册对应索引即可扩展）
- 修改公有 API（`api/` 模块）
- 支持分页（当前接口返回全部，保持现状）

## Decisions

**索引命名用 `spec.providerName`**

遵循 Halo 索引命名惯例（`spec.fieldName`），与 `fieldSelector=spec.providerName=xxx` 的查询语法一致。不采用裸 `providerName`，避免与 metadata 字段混淆。

**使用 `SelectorUtil.labelAndFieldSelectorToListOptions()` 解析查询参数**

直接从 `ServerRequest.queryParams()` 提取 `fieldSelector`/`labelSelector` 字符串列表，通过 Halo 内置的 `SelectorUtil` 统一转换为 `ListOptions`。不需要手动解析每个参数，与 Halo 自动生成 CRUD API 的筛选逻辑一致。

**前端 `useModelsByProvider` 传 `fieldSelector` 参数**

保持独立 query hook，避免缓存污染。`useModels()` 继续拉全部，`useModelsByProvider()` 传 `fieldSelector=spec.providerName=${name}` 走服务端筛选，两者缓存 key 分离。

## Risks / Trade-offs

- **[Risk]** 索引在已有数据上不会自动重建，插件重新安装/升级后首次查询可能略慢 → **Mitigation**: 索引在扩展注册时由 Halo 自动维护，后续写入即索引。
- **[Risk]** `fieldSelector`/`labelSelector` 查询参数需要 OpenAPI 注解才能被生成到 TypeScript client → **Mitigation**: 使用 Springdoc `@Parameter` 注解声明参数，确保 `generateApiClient` 能正确生成带参方法。
