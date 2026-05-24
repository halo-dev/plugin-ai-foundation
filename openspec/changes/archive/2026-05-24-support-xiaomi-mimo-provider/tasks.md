## 1. 新增 Xiaomi MiMo Provider

- [x] 1.1 创建 `XiaomiMiMoProvider.java`，继承 `AbstractAiProviderType` 并添加 `@Component`
- [x] 1.2 设置身份与展示元数据：`providerType = "mimo"`、`displayName = "Xiaomi MiMo"`、description、iconUrl、websiteUrl、documentationUrl
- [x] 1.3 设置默认 base URL 为 `https://api.xiaomimimo.com`，`requiresBaseUrl() = false`，`isBuiltIn() = true`
- [x] 1.4 设置 supported adapter types 为 `List.of(AdapterType.OPENAI_CHAT)`
- [x] 1.5 使用 `OpenAiApi` + `OpenAiChatModel` 实现 `buildChatModel()`，completions path 使用 `/v1/chat/completions`
- [x] 1.6 明确不支持 embedding：`maxEmbeddingsPerCall() = 0`，`supportsParallelCalls() = false`，`buildEmbeddingModel()` 保持返回 `null`

## 2. 品牌资源

- [x] 2.1 确认 `app/src/main/resources/static/brands/xiaomimimo.png` 存在且可作为 provider 图标
- [x] 2.2 确认 `getIconUrl()` 返回 `/plugins/ai-foundation/assets/static/brands/xiaomimimo.png`

## 3. 验证与测试

- [x] 3.1 增加或更新后端测试，覆盖 MiMo provider 元数据、supported adapter types 和 chat model 构建
- [x] 3.2 运行 `./gradlew compileJava`
- [x] 3.3 运行相关后端测试，至少覆盖 provider type 和 provider endpoint 相关测试
- [x] 3.4 启动 Halo 开发服务器后，验证 provider-types API 返回 MiMo
- [x] 3.5 在控制台验证 MiMo 可被选择、配置 API Key、发现或手动创建模型
