## 1. Gradle 多模块项目搭建

- [x] 1.1 重构项目为多模块结构：创建 `api/`、`app/` 目录
- [x] 1.2 更新根目录 `build.gradle` 为父项目（移除插件开发工具）
- [x] 1.3 更新 `settings.gradle` 以包含 `api`、`app`
- [x] 1.4 创建 `api/build.gradle` 作为 `java-library` 并配置 `maven-publish`
- [x] 1.5 创建 `app/build.gradle` 并配置 Halo 插件开发工具和 Spring AI 依赖
- [x] 1.6 将现有 `src/` 内容移至 `app/src/` 或清理旧结构
- [x] 1.7 验证后端模块均能通过 `./gradlew build` 成功构建

## 2. API 模块 —— 公共接口

- [x] 2.1 定义 `LanguageModel` 接口（`chat(String prompt): Mono<String>`、`streamChat(ChatRequest): Flux<ChatChunk>`）
- [x] 2.2 定义 `EmbeddingModel` 接口（`embed(List<String>): Mono<EmbeddingResponse>`、`embed(EmbeddingRequest): Mono<EmbeddingResponse>`、`embedQuery(String): Mono<float[]>`、`maxEmbeddingsPerCall(): int`、`supportsParallelCalls(): boolean`）
- [x] 2.3 定义 `AiModelService` 接口作为 Registry（`languageModel(String modelRef)`、`embeddingModel(String modelRef)`、`listModels()`、`listProviders()`）
- [x] 2.4 定义 `ChatRequest` 数据类（messages、temperature、maxTokens、topP、providerOptions）
- [x] 2.5 定义 `Message` 数据类（role、content）
- [x] 2.6 定义 `ChatChunk` 数据类（type、content、last、finishReason、usage）
- [x] 2.7 定义 `ChunkType` 枚举（TEXT、REASONING、TOOL_CALL、ERROR、FINISH）
- [x] 2.8 定义 `Usage` 数据类（promptTokens、completionTokens）
- [x] 2.9 定义 `EmbeddingResponse` 数据类（embeddings 为 List<float[]>）
- [x] 2.9.1 定义 `EmbeddingRequest` 数据类（inputs、dimensions、maxBatchSize、providerOptions）
- [x] 2.10 定义 `ModelInfo` 数据类（providerName、modelId、displayName，其中 providerName 表示 provider 资源名）
- [x] 2.11 定义 `ProviderInfo` 数据类（name、displayName、providerType、enabled、phase）
- [x] 2.12 定义异常层次结构：`AiFoundationException`、`ModelNotFoundException`、`ProviderDisabledException`、`ProviderApiException`
- [x] 2.13 在 `api/build.gradle` 中添加 `run.halo.tools.platform:plugin:2.23.0` 和 `run.halo.app:api` 依赖
- [x] 2.14 配置 `maven-publish` 在 `api/build.gradle` 中并填写正确的 POM 元数据

## 3. Extension 定义与注册

- [x] 3.1 创建 `AiProvider` Extension 类，标注 `@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiProvider")`
- [x] 3.2 定义 `AiProvider.Spec`，包含 providerType、displayName、enabled、baseUrl、apiKeySecretName，以及用于高级配置的 `config`
- [x] 3.2.1 明确内置 provider type（如 `aihubmix`、`siliconflow`）的默认 `baseUrl` 策略，以及 `openailike` 的自定义 `baseUrl` 策略
- [x] 3.3 定义 `AiProvider.Status`，包含 phase、message、lastCheckedAt 字段
- [x] 3.4 创建 `AiModel` Extension 类，标注 `@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiModel")`
- [x] 3.5 定义 `AiModel.Spec`，包含 providerName（引用 `AiProvider.metadata.name`）、modelId、displayName、group、capabilities、endpointType、supportedTextDelta、enabled
- [x] 3.6 在 `AiFoundationPlugin.start()` 中通过 `SchemeManager` 注册 `AiProvider` 和 `AiModel` 方案
- [x] 3.7 在 `AiFoundationPlugin.stop()` 中注销方案
- [x] 3.8 为 `AiModel` 添加 `(providerName, modelId)` 唯一性校验
- [x] 3.9 实现删除 `AiProvider` 时的引用校验，阻止删除仍被 `AiModel` 引用的 provider
- [x] 3.10 在服务端统一实现唯一性、引用完整性和删除保护校验，确保不能被 UI 绕过

## 4. 提供商实现

- [x] 4.1 创建 `ProviderFactory`，将 providerType 映射到 Spring AI 客户端构建器
- [x] 4.2 创建 `AbstractProviderAdapter` 基类（从 ai-assistant 的 AbstractOpenAiClientProvider 迁移）
- [x] 4.3 实现 OpenAI 提供商的 `OpenAiAdapter`
- [x] 4.3.1 实现 AiHubMix 提供商的 `AiHubMixAdapter`
- [x] 4.4 实现 DeepSeek 提供商的 `DeepSeekAdapter`
- [x] 4.5 实现硅基流动提供商的 `SiliconFlowAdapter`
- [x] 4.6 实现豆包提供商的 `DouBaoAdapter`
- [x] 4.7 实现文心一言提供商的 `ErnieAdapter`
- [x] 4.8 实现智谱AI 提供商的 `ZhiPuAdapter`
- [x] 4.9 实现 Ollama 提供商的 `OllamaAdapter`
- [x] 4.10 实现 OpenAI 兼容提供商的 `OpenAiLikeAdapter`
- [x] 4.10.1 为 `aihubmix`、`siliconflow` 等内置 provider adapter 固化默认 `baseUrl` 和必要的 provider-specific 请求头/行为
- [x] 4.11 通过配置映射实现按提供商代理支持（proxyHost、proxyPort）
- [x] 4.12 为每个提供商实现模型列表（从 ai-assistant 的 *Model 类迁移）
- [x] 4.13 添加提供商客户端缓存，并在 Extension 更新时刷新
- [x] 4.14 为每个 provider adapter 实现 `providerOptions` 解析（如 OpenAI 的 logitBias）
- [x] 4.15 为每个 provider adapter 实现 `maxEmbeddingsPerCall()` 和 `supportsParallelCalls()`
- [x] 4.16 支持 `AiProvider.spec.apiKeySecretName` 单 Secret 配置，并通过 Halo Secret 解析真实凭据

## 5. AiModelService 实现

- [x] 5.1 实现 `AiModelServiceImpl` 作为 Registry，解析 `modelRef` 并返回对应的能力接口
- [x] 5.2 实现 `languageModel(String modelRef)`，解析 `providerResourceName/modelId`（其中 `providerResourceName = AiProvider.metadata.name`），返回 `LanguageModel` 实例
- [x] 5.3 实现 `embeddingModel(String modelRef)`，解析 `providerResourceName/modelId`（其中 `providerResourceName = AiProvider.metadata.name`），返回 `EmbeddingModel` 实例
- [x] 5.4 实现 `LanguageModelImpl.chat(String prompt)`，调用底层 Spring AI 客户端并返回 `Mono<String>`
- [x] 5.5 实现 `LanguageModelImpl.streamChat(ChatRequest)`，返回标准化 `Flux<ChatChunk>`（含 type、usage、finishReason）
- [x] 5.6 实现 `EmbeddingModelImpl.embed(List<String>)`，内部自动分块（按 `maxEmbeddingsPerCall`）和并行调用
- [x] 5.7 实现 `listModels()`，返回 `Mono<List<ModelInfo>>` 查询所有 `AiModel` Extension
- [x] 5.8 实现 `listProviders()`，返回 `Mono<List<ProviderInfo>>` 查询所有 `AiProvider` Extension
- [x] 5.9 实现错误处理：无效 modelRef 格式、未配置模型、已禁用提供商（抛出 typed exceptions）
- [x] 5.10 将 `AiModelServiceImpl` 注册为 Spring `@Component` Bean

## 6. 调试 / 管理端点

- [x] 6.1 创建 `ProviderDebugEndpoint`（或等价命名）实现 `CustomEndpoint`
- [x] 6.2 实现 `GET /providers/{name}/models` 用于从提供商 API 获取模型列表
- [x] 6.3 实现 `POST /providers/{name}/connectivity` 用于连通性测试
- [x] 6.4 实现 `POST /providers/{providerName}/models/{modelId}/test-chat`，请求体接收 `prompt`，用于管理员快速验证模型可用性
- [x] 6.5 为端点添加 OpenAPI 文档注解
- [x] 6.6 将 `groupVersion` 配置为 `console.api.aifoundation.halo.run/v1alpha1`
- [x] 6.7 为 provider 详情页提供按 provider 聚合查询其关联 `AiModel` 的接口或查询封装

## 7. 插件元数据与安全

- [x] 7.1 更新 `app/src/main/resources/plugin.yaml`，添加 `pluginDependencies`、`requires` 和正确的元数据
- [x] 7.2 创建 `app/src/main/resources/extensions/roleTemplate.yaml` 用于 RBAC 权限
- [x] 7.3 如需为 Console API 配置代理，创建 `app/src/main/resources/extensions/reverseProxy.yaml`
- [x] 7.4 确保 API 端点受适当的角色模板保护
- [x] 7.5 确认插件运行时具备读取所引用 Halo Secret 的权限边界

## 8. 构建与验证

- [x] 8.1 验证 `./gradlew :api:build` 成功并生成可发布的 jar
- [x] 8.2 验证 `./gradlew :app:build` 成功并生成插件 jar
- [x] 8.3 运行 `./gradlew :app:test` 并确保测试通过
- [x] 8.4 在 Halo 开发模式下测试插件启动（`./gradlew :app:haloServer`）
- [x] 8.5 验证插件启动后 `AiProvider` 和 `AiModel` Extension 出现在 Halo API 中
- [x] 8.6 通过调试接口验证 `test-chat` 端点可使用 `providerResourceName/modelId + prompt` 发起测试请求
