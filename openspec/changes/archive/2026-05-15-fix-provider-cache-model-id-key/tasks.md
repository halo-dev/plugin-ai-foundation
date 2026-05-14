## 1. ProviderClientCache 缓存键修复

- [x] 1.1 修改 `ProviderClientCache.getOrCreateChatModel()` 缓存键为 `providerName + "/" + modelId`
- [x] 1.2 修改 `ProviderClientCache.getOrCreateEmbeddingModel()` 缓存键为 `providerName + "/" + modelId`
- [x] 1.3 修改 `ProviderClientCache.invalidate(String providerName)` 以 `providerName + "/"` 前缀匹配并删除所有关联缓存
- [x] 1.4 运行 `./gradlew test` 确保现有测试通过

## 2. 验证

- [x] 2.1 启动 Halo dev server (`./gradlew haloServer`)
- [x] 2.2 在 UI 中为同一 provider 配置两个不同 modelId 的模型
- [x] 2.3 分别测试两个模型的 chat completion，确认调用的是各自对应的模型
