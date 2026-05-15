## Context

当前 `ProviderConsoleEndpoint.listProviders()`、`ModelConsoleEndpoint.listModels()`、`AiModelServiceImpl.listProviders()` 和 `AiModelServiceImpl.listModels()` 四处在查询 Extension 数据时都传递 `Sort.unsorted()`，导致返回的顺序由底层存储引擎决定，不稳定且不直观。

Halo 核心代码中，`ReactiveExtensionPaginatedOperatorImpl.createPageRequest()` 默认使用 `Sort.by("metadata.creationTimestamp", "metadata.name")`，说明按创建时间排序是 Halo 的惯例。

## Goals / Non-Goals

**Goals:**
- 统一 Providers 和 Models 的列表默认排序为创建时间倒序（最新在前）
- 覆盖 Console API 和公开 API（`AiModelService`）两个层面

**Non-Goals:**
- 支持客户端自定义排序参数
- 添加分页
- 修改前端排序逻辑

## Decisions

### 后端排序而非前端排序
- **Rationale**: 后端排序一次完成，所有调用者（Console 前端、其他插件通过 `AiServices`）自动获得一致顺序，无需在前端重复实现。
- **Alternative considered**: 前端在 composable 中用 `select` 排序 — 简单但只影响当前前端，公开 API 调用者仍需自己排序。

### 只按 `metadata.creationTimestamp` 倒序，不加第二排序字段
- **Rationale**: 用户明确确认不需要。creationTimestamp 在绝大多数情况下已足够区分先后顺序。Halo 的分页操作加 `metadata.name` 是为了保证分页边界的稳定性，我们的场景是无分页全量列表，不需要 tie-breaker。

### 同时修改 `AiModelServiceImpl` 的公开 API
- **Rationale**: `AiModelService.listProviders()` 和 `listModels()` 被其他插件通过 `AiServices.getModelService()` 调用。统一排序避免调用者拿到无序数据后自行处理。

## Risks / Trade-offs

- **[Risk]** 其他插件可能依赖了 `AiModelService` 返回的无序/原有顺序 → **Mitigation**: 本插件尚未发布，backward compatibility 不是 concern（CLAUDE.md 明确说明）。即使有影响，按时间倒序是更合理的行为。
