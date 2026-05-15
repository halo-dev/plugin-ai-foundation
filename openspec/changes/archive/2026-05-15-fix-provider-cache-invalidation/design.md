## Context

`ProviderClientCache` 目前仅在显式调用 `invalidate()` / `invalidateAll()` 时清理缓存，但代码中没有任何地方在 Provider 配置变更后触发失效。这导致：

1. 用户修改 `AiProvider` 的 `apiKeySecretName`、`baseUrl` 等字段后，后续 AI 调用仍使用旧的 `ChatModel`/`EmbeddingModel` 实例。
2. 用户通过 Halo Secret 管理界面轮换 API Key 后，缓存中的旧凭证继续被使用。

现有 spec（`ai-provider-config`）已要求缓存随 Provider 更新刷新，但实现缺失。

## Goals / Non-Goals

**Goals:**
- `AiProvider` Extension 被更新或删除时，自动失效该 provider 对应的所有缓存模型。
- 被 `AiProvider` 引用的 Secret Extension 被更新时，自动失效引用该 Secret 的所有 provider 缓存。
- 实现不依赖 Console API 路径（因为 Provider 也可能通过 Core API 直接更新）。

**Non-Goals:**
- 不在 `ProviderConsoleEndpoint` 层手动调用缓存失效（问题 9 指出编辑走 Core API，console endpoint 层无法覆盖所有路径）。
- 不引入外部缓存系统（如 Redis）。

## Decisions

### 决策 1：使用 Halo `Watcher` 机制监听 Extension 变更

**理由**: Halo 的 `ReactiveExtensionClient` 在 `update()` 内部会调用所有注册 `Watcher` 的 `onUpdate` 方法。通过注册一个 `Watcher` bean，无论通过 Console API 还是 Core API 更新 `AiProvider`，都能触发回调。这是唯一覆盖所有更新路径的方案。

**替代方案**: 在 `ProviderConsoleEndpoint.updateProvider()` 中手动调用 `invalidate()`。被放弃，因为问题 9 指出 Provider 编辑走 Core API，Console Endpoint 无法覆盖所有路径。

### 决策 2：Watcher 同时监听 `AiProvider` 和 `Secret`

**理由**: `apiKeySecretName` 引用的是 Halo Secret Extension。Secret 内容变更（如 API Key 轮换）时，缓存也必须失效。

**实现**: `onUpdate` 回调中通过 `extension.groupVersionKind()` 判断类型：
- 若为 `AiProvider`，直接调用 `providerClientCache.invalidate(providerName)`。
- 若为 `Secret`，查询所有 `spec.apiKeySecretName` 等于该 Secret 名称的 `AiProvider`，逐个失效。

### 决策 3：在 `@PostConstruct` 中注册 Watcher

**理由**: Halo 的 `ReactiveExtensionClient.watch(Watcher)` 方法用于注册 Watcher。在 Spring bean 初始化时注册，确保插件启动后立即生效。

## Risks / Trade-offs

- **[Risk]** Secret 更新时查询所有 `AiProvider` 可能带来轻微性能开销（全表扫描）。
  → **Mitigation**: Provider 数量通常极少（< 100），且 Secret 更新频率极低，可接受。
- **[Risk]** Watcher 在 reactive 线程中回调，若查询操作阻塞可能影响性能。
  → **Mitigation**: `invalidate()` 操作是纯内存 Map 操作，无 IO，不会阻塞。
