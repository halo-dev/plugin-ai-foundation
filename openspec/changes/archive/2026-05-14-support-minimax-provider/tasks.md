## 1. 创建 MiniMax Provider 后端组件

- [x] 1.1 创建 `MiniMaxProvider.java`，继承 `AbstractAiProviderType`，实现 `AiProviderType` 接口
- [x] 1.2 配置 provider 元数据：providerType="minimax"、displayName="MiniMax"、description、iconUrl、websiteUrl、documentationUrl
- [x] 1.3 实现 `buildChatModel()` 方法，使用 `OpenAiChatModel.builder()` 构建 chat model
- [x] 1.4 设置默认 base URL 为 `https://api.minimax.io`，completions path 为 `/v1/chat/completions`
- [x] 1.5 设置 `supportsEmbeddings() = false`、`maxEmbeddingsPerCall() = 0`、`supportsParallelCalls() = false`
- [x] 1.6 添加 `@Component` 注解，确保 Spring 自动扫描注册

## 2. 添加品牌图标资源

- [x] 2.1 准备 MiniMax 品牌图标（PNG 格式，建议 64x64 或 128x128）
- [x] 2.2 将图标放入 `app/src/main/resources/static/brands/minimax.png`
- [x] 2.3 确认 `iconUrl` 路径与图标文件位置一致

## 3. 验证与测试

- [x] 3.1 运行 `./gradlew compileJava` 确保后端编译通过
- [x] 3.2 运行 `./gradlew test` 确保现有测试不受影响
- [x] 3.3 启动 Halo 开发服务器，验证 provider-types API 列表中包含 MiniMax
- [x] 3.4 在控制台 UI 中验证 MiniMax 提供商可正常选择并配置
