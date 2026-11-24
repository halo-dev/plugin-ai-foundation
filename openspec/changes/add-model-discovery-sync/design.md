# 模型发现同步设计

## Context

动机和范围见 `proposal.md`，外部行为见 `specs/model-discovery-sync/spec.md`。本设计以现有
`ProviderConsoleEndpoint.discover-models`、`AiModel` 字段语义和发现弹窗为基础，不新增持久化字段。

## Goals / Non-Goals

**Goals:**

- 发现接口直接给出可执行的同步建议，Console 不需要自己推断本地状态。
- 同步不会静默改写管理员维护的字段。
- 批量操作只返回逐项结果，单个模型失败不影响其余模型。

**Non-Goals:**

- 后台定时同步、远端消失自动删除、跨供应商迁移。

## Decisions

### 发现接口返回同步状态

`DiscoveredModelItem` 增加 `syncStatus`、`localModelName`、`localEnabled`、
`localDisplayName`、`syncable` 和 `differences`。`ProviderModelDiscoveryResponse` 增加
`missingModels`。

- 以 `AiModel.spec.modelId` 匹配同一供应商下的本地模型。
- `syncStatus`：无本地模型为 `new`；本地存在且启用为 `existing`；本地存在但禁用为
  `disabled`。远端不再返回的本地模型放入 `missingModels`，不删除。
- `syncable`：本地模型的 `discoverySource` 不是 `manual`。手动创建的模型只展示差异，
  同步不会写入。

### 差异计算与字段归属

后端按字段比较本地快照和远端发现结果，返回结构化差异，而不是让 UI 猜测：

- 发现维护：`modelType`、`adapterType`、`features`、`capabilities`、`capabilitySources`、
  `catalogMatch`、`catalogParameters`、`discoverySource`、`discoveryConfidence`。
- 管理员维护：`displayName`、`enabled`、`parameterMappings`、`parameterConstraints`、
  `nativeOptions`。差异中 `applied=false`。
- 能力领域来源为 `manual` 时，该领域的差异同样 `applied=false`。

差异值使用稳定的字符串（类型、适配器、特性列表、能力值），Console 负责本地化标签。

### 批量同步端点

`POST providers/{name}/sync-models` 接收操作列表，每个操作包含
`action`（`create`/`update`/`enable`/`disable`/`ignore`）、供应商侧 `modelId`、本地资源名，
创建时携带完整 `AiModel`，更新时携带发现结果中的远端画像。

- `create`：沿用模型校验、唯一性检查与资源名生成规则。
- `update`：只写入发现维护字段；保留管理员字段和 `manual` 能力领域。
- `enable`/`disable`：只修改 `enabled`。
- `ignore`：不执行写入，返回 `skipped`。
- 操作按顺序执行，逐项捕获错误并返回 `failed` 结果。
- 响应包含逐项 `outcome` 和汇总计数，Console 据此提示并刷新列表。

### Console 交互

- 按同步状态分组：新增 / 已存在 / 本地已禁用 / 远端已消失。
- 新增模型默认选中；已存在模型只有明确勾选才执行同步。
- 页脚提供创建、同步更新、启用、禁用按钮，只对匹配状态的已选模型生效。
- 执行前使用影响范围确认，说明管理员字段不会被覆盖。
- 同步后刷新发现结果和供应商模型列表，弹窗保持打开。
- 远端消失分组只展示警告，不提供删除操作。
