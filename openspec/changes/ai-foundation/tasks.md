## 1. Gradle 多模块项目搭建

- [ ] 1.1 重构项目为多模块结构：创建 `api/`、`app/`、`ui/` 目录
- [ ] 1.2 更新根目录 `build.gradle` 为父项目（移除插件开发工具）
- [ ] 1.3 更新 `settings.gradle` 以包含 `api`、`app`、`ui`
- [ ] 1.4 创建 `api/build.gradle` 作为 `java-library` 并配置 `maven-publish`
- [ ] 1.5 创建 `app/build.gradle` 并配置 Halo 插件开发工具和 Spring AI 依赖
- [ ] 1.6 将现有 `src/` 内容移至 `app/src/` 或清理旧结构
- [ ] 1.7 验证所有模块均能通过 `./gradlew build` 成功构建

## 2. API 模块 —— 公共接口

- [ ] 2.1 定义 `LanguageModel` 接口（`chat(String prompt): Mono<String>`、`streamChat(ChatRequest): Flux<ChatChunk>`）
- [ ] 2.2 定义 `EmbeddingModel` 接口（`embed(List<String>): Mono<EmbeddingResponse>`、`maxEmbeddingsPerCall(): int`、`supportsParallelCalls(): boolean`）
- [ ] 2.3 定义 `AiModelService` 接口作为 Registry（`languageModel(String modelRef)`、`embeddingModel(String modelRef)`、`listModels()`、`listProviders()`）
- [ ] 2.4 定义 `ChatRequest` 数据类（messages、temperature、maxTokens、topP、providerOptions）
- [ ] 2.5 定义 `Message` 数据类（role、content）
- [ ] 2.6 定义 `ChatChunk` 数据类（type、content、last、finishReason、usage）
- [ ] 2.7 定义 `ChunkType` 枚举（TEXT、REASONING、TOOL_CALL、ERROR、FINISH）
- [ ] 2.8 定义 `Usage` 数据类（promptTokens、completionTokens）
- [ ] 2.9 定义 `EmbeddingResponse` 数据类（embeddings 为 List<float[]>）
- [ ] 2.10 定义 `ModelInfo` 数据类（providerName、modelId、displayName）
- [ ] 2.11 定义 `ProviderInfo` 数据类（name、displayName、providerType、enabled、phase）
- [ ] 2.12 定义异常层次结构：`AiFoundationException`、`ModelNotFoundException`、`ProviderDisabledException`、`ProviderApiException`
- [ ] 2.13 在 `api/build.gradle` 中添加 `run.halo.tools.platform:plugin:2.23.0` 和 `run.halo.app:api` 依赖
- [ ] 2.14 配置 `maven-publish` 在 `api/build.gradle` 中并填写正确的 POM 元数据

## 3. Extension 定义与注册

- [ ] 3.1 创建 `AiProvider` Extension 类，标注 `@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiProvider")`
- [ ] 3.2 定义 `AiProvider.Spec`，包含 providerType、displayName、config（Map<String,String>）、enabled
- [ ] 3.3 定义 `AiProvider.Status`，包含 phase 和 message 字段
- [ ] 3.4 创建 `AiModel` Extension 类，标注 `@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiModel")`
- [ ] 3.5 定义 `AiModel.Spec`，包含 providerName、modelId、displayName
- [ ] 3.6 在 `AiFoundationPlugin.start()` 中通过 `SchemeManager` 注册 `AiProvider` 和 `AiModel` 方案
- [ ] 3.7 在 `AiFoundationPlugin.stop()` 中注销方案

## 4. 提供商实现

- [ ] 4.1 创建 `ProviderFactory`，将 providerType 映射到 Spring AI 客户端构建器
- [ ] 4.2 创建 `AbstractProviderAdapter` 基类（从 ai-assistant 的 AbstractOpenAiClientProvider 迁移）
- [ ] 4.3 实现 OpenAI 提供商的 `OpenAiAdapter`
- [ ] 4.4 实现 DeepSeek 提供商的 `DeepSeekAdapter`
- [ ] 4.5 实现硅基流动提供商的 `SiliconFlowAdapter`
- [ ] 4.6 实现豆包提供商的 `DouBaoAdapter`
- [ ] 4.7 实现文心一言提供商的 `ErnieAdapter`
- [ ] 4.8 实现智谱AI 提供商的 `ZhiPuAdapter`
- [ ] 4.9 实现 Ollama 提供商的 `OllamaAdapter`
- [ ] 4.10 实现 OpenAI 兼容提供商的 `OpenAiLikeAdapter`
- [ ] 4.11 通过配置映射实现按提供商代理支持（proxyHost、proxyPort）
- [ ] 4.12 为每个提供商实现模型列表（从 ai-assistant 的 *Model 类迁移）
- [ ] 4.13 添加提供商客户端缓存，并在 Extension 更新时刷新
- [ ] 4.14 为每个 provider adapter 实现 `providerOptions` 解析（如 OpenAI 的 logitBias）
- [ ] 4.15 为每个 provider adapter 实现 `maxEmbeddingsPerCall()` 和 `supportsParallelCalls()`

## 5. AiModelService 实现

- [ ] 5.1 实现 `AiModelServiceImpl` 作为 Registry，解析 `modelRef` 并返回对应的能力接口
- [ ] 5.2 实现 `languageModel(String modelRef)`，解析 `providerName/modelId`，返回 `LanguageModel` 实例
- [ ] 5.3 实现 `embeddingModel(String modelRef)`，解析 `providerName/modelId`，返回 `EmbeddingModel` 实例
- [ ] 5.4 实现 `LanguageModelImpl.chat(String prompt)`，调用底层 Spring AI 客户端并返回 `Mono<String>`
- [ ] 5.5 实现 `LanguageModelImpl.streamChat(ChatRequest)`，返回标准化 `Flux<ChatChunk>`（含 type、usage、finishReason）
- [ ] 5.6 实现 `EmbeddingModelImpl.embed(List<String>)`，内部自动分块（按 `maxEmbeddingsPerCall`）和并行调用
- [ ] 5.7 实现 `listModels()`，返回 `Mono<List<ModelInfo>>` 查询所有 `AiModel` Extension
- [ ] 5.8 实现 `listProviders()`，返回 `Mono<List<ProviderInfo>>` 查询所有 `AiProvider` Extension
- [ ] 5.9 实现错误处理：无效 modelRef 格式、未配置模型、已禁用提供商（抛出 typed exceptions）
- [ ] 5.10 将 `AiModelServiceImpl` 注册为 Spring `@Component` Bean

## 6. Console 端点

- [ ] 6.1 创建 `ProviderConsoleEndpoint` 实现 `CustomEndpoint`
- [ ] 6.2 实现 `GET /providers/{name}/models` 用于从提供商 API 获取模型列表
- [ ] 6.3 实现 `POST /providers/{name}/connectivity` 用于连通性测试
- [ ] 6.4 为端点添加 OpenAPI 文档注解
- [ ] 6.5 将 `groupVersion` 配置为 `console.api.aifoundation.halo.run/v1alpha1`

## 7. Vue Console UI

- [ ] 7.1 在 `ui/src/index.ts` 中于系统菜单下注册 Console 路由
- [ ] 7.2 创建 `ProviderManager.vue` 作为主管理页面，包含提供商列表和模型列表两个标签页
- [ ] 7.3 创建 `ProviderCard.vue` 用于展示单个提供商信息
- [ ] 7.4 创建 `ProviderForm.vue` 弹窗用于创建/编辑提供商
- [ ] 7.5 创建 `ModelCard.vue` 用于展示单个模型信息（格式：提供商 / 模型）
- [ ] 7.6 创建 `ModelForm.vue` 弹窗用于添加/编辑模型（选择提供商 + 输入 modelId + 显示名称）
- [ ] 7.7 实现提供商增删改查操作，使用 `@tanstack/vue-query` 和 `axiosInstance`
- [ ] 7.8 实现模型增删改查操作，使用 `@tanstack/vue-query` 和 `axiosInstance`
- [ ] 7.9 实现从提供商 API 获取模型列表并批量添加的功能
- [ ] 7.10 实现连通性测试按钮，包含加载状态和结果展示
- [ ] 7.11 添加表单校验（提供商类型的必填字段、模型的 providerName 和 modelId 必填）
- [ ] 7.12 使用 `@halo-dev/components` 和 UnoCSS 进行 UI 样式设计

## 8. 插件元数据与安全

- [ ] 8.1 更新 `app/src/main/resources/plugin.yaml`，添加 `pluginDependencies`、`requires` 和正确的元数据
- [ ] 8.2 创建 `app/src/main/resources/extensions/roleTemplate.yaml` 用于 RBAC 权限
- [ ] 8.3 如需为 Console API 配置代理，创建 `app/src/main/resources/extensions/reverseProxy.yaml`
- [ ] 8.4 确保 API 端点受适当的角色模板保护

## 9. 构建与验证

- [ ] 9.1 验证 `./gradlew :api:build` 成功并生成可发布的 jar
- [ ] 9.2 验证 `./gradlew :app:build` 成功并生成插件 jar
- [ ] 9.3 验证 `./gradlew :ui:build` 成功并生成 dist 资源
- [ ] 9.4 运行 `./gradlew :app:test` 并确保测试通过
- [ ] 9.5 验证插件 jar 在 `console/` 目录中包含 UI 资源
- [ ] 9.6 在 Halo 开发模式下测试插件启动（`./gradlew :app:haloServer`）
- [ ] 9.7 验证插件启动后 `AiProvider` 和 `AiModel` Extension 出现在 Halo API 中
