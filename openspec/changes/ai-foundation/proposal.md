## 背景

目前仓库仍是 Halo plugin starter 形态，尚未形成可复用的 AI 基础设施。与此同时，每个需要 AI 能力的 Halo 插件（如 plugin-ai-assistant）都必须独立实现模型厂商配置和 AI 客户端管理。如果多个插件需要 AI 功能，用户必须重复配置 API 密钥，开发者也要重复编写提供商实现代码。一个集中式的 AI 基础设施插件可以统一模型配置并暴露可复用的 Java SDK，同时解决用户和开发者的痛点。

## 变更内容

> 本 change 收敛为 **backend foundation phase**。Console UI 已拆分为独立的 `ai-foundation-console` change；当前 change 先完成数据模型、服务端校验、provider 适配与调试接口。

- **将当前 starter 项目重构为 Gradle 多模块插件**（`api`、`app`），提供集中式 AI 模型基础设施
- **`api/` 模块**：发布到 Maven Central，暴露能力分离的接口 —— `LanguageModel`（对话/流式对话）和 `EmbeddingModel`（向量嵌入），`AiModelService` 作为 Registry 按 `providerResourceName/modelId` 返回具体能力接口，其中 `providerResourceName` 对应 `AiProvider.metadata.name`。所有类型均为包装类型（非 Spring AI 原生）
- **`app/` 模块**：插件运行时，实现 `AiModelService`，将 AI 提供商配置与模型元数据管理为 Halo Extension
- **新建 Extension `AiProvider`**（`aifoundation.halo.run/v1alpha1`）：存储提供商实例配置，包含提供商类型、显示名称、启用状态、结构化连接字段（如 `baseUrl`、`apiKeySecretName`），并通过 Halo Secret 存储敏感凭据，以及用于扩展高级选项的 `config`
- **内置厂商预设优先**：对于 AiHubMix、硅基流动、OpenAI、DeepSeek 等内置 provider type，用户在 Console 中直接选择厂商并配置 `apiKeySecretName` 即可；仅 `openailike` 作为兜底类型要求用户填写自定义 `baseUrl`
- **新建 Extension `AiModel`**（`aifoundation.halo.run/v1alpha1`）：存储模型定义，除 `providerName`（即 provider 资源名，引用 `AiProvider.metadata.name`）、`modelId`、`displayName` 外，还包含分组、能力标签、端点类型、流式兼容性等面向 Console 管理的元数据
- **提供商实现**：从 plugin-ai-assistant 迁移提供商适配器，支持 AiHubMix、OpenAI、DeepSeek、硅基流动、豆包、文心一言、智谱AI、Ollama 和 OpenAI 兼容模型；第一阶段按 provider 实际能力暴露 chat / embedding，而非要求所有 provider 同时支持两类能力
- **流式响应标准化**：`ChatChunk` 扩展为包含 `type`（TEXT/REASONING/FINISH 等）、`finishReason`、`usage`（token 统计）的结构，为未来支持 reasoning、tool calling 预留扩展空间
- **错误类型体系**：`api/` 模块定义 `AiFoundationException` 异常层次结构（`ModelNotFoundException`、`ProviderDisabledException`、`ProviderApiException`），消费插件可通过 `instanceof` 精确处理错误
- **Provider Options 透传**：`ChatRequest` 支持 `Map<String, Object> providerOptions`，允许消费插件传递 provider-specific 参数（如 OpenAI 的 `logitBias`）而不污染通用 API
- **调试 / 管理端点**：自定义端点，用于模型列表（`/providers/{name}/models`）、连通性测试（`/providers/{name}/connectivity`）和调试对话测试（`/providers/{providerName}/models/{modelId}/test-chat`）
- **不支持 ExtensionPoint 自定义提供商**：所有提供商内置于本插件，以确保一致的行为和安全性
- **无 Reconciler**：提供商状态仅通过手动连通性测试管理
- **不支持图像生成**：第一阶段仅覆盖对话和向量嵌入
- **资源保持分离，交互后置聚合**：存储层继续使用 `AiProvider` 与 `AiModel` 两类 Extension；后续 UI change 将按 provider 聚合展示其配置与关联模型，以兼顾 Halo Extension 设计和 Cherry Studio 式产品体验
- **补充数据约束**：`AiModel.spec.providerName` 必须引用 `AiProvider.metadata.name`；`AiModel` 在同一 provider 下必须保证 `modelId` 唯一；删除仍被 `AiModel` 引用的 `AiProvider` 时应阻止删除，而不是隐式级联删除
- **补充密钥与状态语义**：Provider 凭据通过 Halo Secret 引用；Console 不直接持久化明文 API Key；连通性测试会更新 `status.phase`、`status.message` 与 `status.lastCheckedAt`
- **补充校验策略**：唯一性、引用完整性、删除保护等约束以服务端校验为准，UI 只做前置提示

## 能力

### 新增能力

- `ai-provider-config`：基于 Extension 的 AI 提供商配置管理（创建、读取、更新、删除、测试连通性），支持结构化连接字段与高级配置扩展
- `ai-model-service`：供其他插件调用 AI 能力的编程 API。`AiModelService` 作为 Registry 返回 `LanguageModel`（对话/流式对话）或 `EmbeddingModel`（向量嵌入）接口实例。流式响应通过标准化的 `ChatChunk`（含 type、usage、finishReason）传递。支持 `providerOptions` 透传 provider-specific 参数
- `provider-debug-api`：面向管理员或开发调试的服务端接口，支持模型发现、连通性测试和按 `providerResourceName/modelId + prompt` 发起测试对话

### 修改的能力

- （无 —— 这是一个新插件，没有现有能力需要修改）

## 影响

- **新的插件依赖**：消费者插件（如 plugin-ai-assistant）将在其 `plugin.yaml` 中声明 `pluginDependencies: ai-foundation: 1.*`
- **新的编译依赖**：消费者插件在其构建中添加 `compileOnly 'run.halo.aifoundation:api:x.x.x'`
- **稳定的消费边界**：消费者插件仅依赖 `api/` 模块；`app/` 内部的 Spring AI 与 provider 实现细节不对外暴露
- **分阶段交付**：本次 change 先交付 backend foundation；Console workspace 在独立的 `ai-foundation-console` change 中推进
- **plugin-ai-assistant 的迁移路径**：未来阶段将把 ai-assistant 迁移为依赖 ai-foundation，而非其自己的提供商实现；本次 proposal 不包含 ai-assistant 的实际迁移
- **Spring AI 2.0.0-M2**：插件打包 Spring AI 库；消费者插件不需要 Spring AI 依赖
- **Halo 2.23+**：最低支持的 Halo 版本
