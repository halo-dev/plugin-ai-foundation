## Context

`ProviderClientCache` 维护两个 `ConcurrentHashMap`：
- `chatModelCache`: `Map<String, ChatModel>`
- `embeddingModelCache`: `Map<String, EmbeddingModel>`

当前缓存键仅为 `provider.getMetadata().getName()`（即 provider resource name）。但一个 provider 下可配置多个 `AiModel`，每个模型有不同的 `modelId`。`AiProviderType.buildChatModel()` 在 `defaultOptions` 中将 `modelId` 固化到 `ChatModel` 实例中。因此，同一 provider 下的多个模型如果共享缓存实例，后缓存的模型实际调用的是先缓存模型的 `modelId`。

## Goals / Non-Goals

**Goals:**
- 确保同一 provider 下的不同 `modelId` 各自拥有独立的 `ChatModel`/`EmbeddingModel` 缓存实例
- `invalidate(String providerName)` 仍能正确清理该 provider 下的所有缓存

**Non-Goals:**
- 不修改 `AiProviderType` 接口或任何 provider 实现
- 不引入缓存过期/TTL 机制
- 不涉及前端变更

## Decisions

**缓存键格式: `providerName + "/" + modelId`**

- `providerName` 保持前缀，便于按 provider 批量清理
- 使用 `/` 分隔符，与 `modelRef` 格式（`providerResourceName/modelId`）保持一致
- 替代方案：使用嵌套 `Map<String, Map<String, ChatModel>>`（provider → modelId → model）。否决原因：增加复杂度，`invalidate()` 需要遍历内层 map；当前扁平键方案足够简单且有效

**`invalidate(String providerName)` 实现**

- 遍历缓存 `keySet()`，删除所有以 `providerName + "/"` 开头的键
- 替代方案：维护反向索引（providerName → Set<key>）。否决原因：引入额外的同步复杂度；键遍历在缓存规模小（provider × model 数量）时性能可接受

## Risks / Trade-offs

- [缓存键冲突风险] → `providerName` 和 `modelId` 中若包含 `/` 字符可能导致键解析歧义。但 Halo Extension 的 `metadata.name` 是 DNS 子域名格式，不含 `/`；`modelId` 通常也不含 `/`（前端导入时已将 `/` 替换为 `-`）
