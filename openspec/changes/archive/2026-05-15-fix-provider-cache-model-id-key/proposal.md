## Why

ProviderClientCache 使用 `provider.metadata.name` 作为缓存键，但同一 provider 下可能有多个模型（不同 `modelId`）。这导致所有模型共享同一个 `ChatModel`/`EmbeddingModel` 实例，而实例的 `defaultOptions` 中已固定了 `modelId`，因此调用非首缓存模型时会实际使用错误的模型。

## What Changes

- 修改 `ProviderClientCache` 的缓存键，从仅使用 provider name 改为使用 `providerName + "/" + modelId` 的复合键
- 同步更新 `invalidate()` 方法，使其能正确清理指定 provider 下的所有模型缓存
- 纯后端实现修复，无 API 变更，无 UI 变更

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

（无 — 此为纯实现缺陷修复，不涉及 spec-level 行为变更）

## Impact

- `app/src/main/java/run/halo/aifoundation/provider/support/ProviderClientCache.java`
- `app/src/test/java/...` 中相关测试用例（如有）
