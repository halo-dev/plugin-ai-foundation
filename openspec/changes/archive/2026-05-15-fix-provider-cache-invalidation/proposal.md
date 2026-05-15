## Why

Provider 配置更新（API key、baseUrl 等）后，`ProviderClientCache` 中缓存的 `ChatModel`/`EmbeddingModel` 实例不会自动失效，导致后续调用仍使用旧配置。这是一个已有的 spec 需求（`ai-provider-config` 已要求缓存随 Provider 更新刷新），但实现尚未落地。

## What Changes

- 在 `ProviderClientCache` 中新增按 provider 名称使缓存失效的方法。
- 监听 Halo Extension 的 `UpdatedEvent`（`AiProvider` 资源变更时），自动调用缓存失效逻辑。
- 监听 Halo Secret 的 `UpdatedEvent`（Secret 轮换时），自动使引用该 Secret 的所有 Provider 缓存失效。

## Capabilities

### New Capabilities
- *(none — this is a bug fix to existing capability)*

### Modified Capabilities
- `ai-provider-config`: 补全 **Provider client caching** 需求的实现。现有 spec 已要求缓存随 Provider 更新刷新，但当前代码无事件监听机制。此 change 通过 `ExtensionUpdatedEvent` 监听实现该需求，无需修改 spec 文本。

## Impact

- **Backend**: `ProviderClientCache.java`、`ProviderConsoleEndpoint.java`，新增事件监听类。
- **API**: 无 API 变更。
- **Frontend**: 无变更。
