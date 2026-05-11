## 背景

目前，每个需要 AI 能力的 Halo 插件（如 plugin-ai-assistant）都必须独立实现模型厂商配置和 AI 客户端管理。如果多个插件需要 AI 功能，用户必须重复配置 API 密钥，开发者也要重复编写提供商实现代码。一个集中式的 AI 基础设施插件可以统一模型配置并暴露可复用的 Java SDK，同时解决用户和开发者的痛点。

## 变更内容

- **新建 Gradle 多模块插件**（`api`、`app`、`ui`），提供集中式 AI 模型基础设施
- **`api/` 模块**：发布到 Maven Central，暴露 `AiModelService` 接口，使用包装类型（非 Spring AI 原生类型）支持对话、流式对话和向量嵌入
- **`app/` 模块**：插件运行时，实现 `AiModelService`，将 AI 提供商配置管理为 Halo Extension
- **`ui/` 模块**：基于 Vue 的 Console 页面，对 `AiProvider` 和 `AiModel` Extension 进行增删改查操作，包括连通性测试和模型管理
- **新建 Extension `AiProvider`**（`aifoundation.halo.run/v1alpha1`）：存储提供商配置（提供商类型、显示名称、配置映射、启用状态）
- **新建 Extension `AiModel`**（`aifoundation.halo.run/v1alpha1`）：存储模型定义（关联的提供商、模型 ID、展示名称），从已配置的提供商中添加模型
- **提供商实现**：从 plugin-ai-assistant 迁移 OpenAI 兼容的提供商适配器，支持 OpenAI、DeepSeek、硅基流动、豆包、文心一言、智谱AI、Ollama 和 OpenAI 兼容模型
- **Console 端点**：自定义端点，用于模型列表（`/providers/{name}/models`）和连通性测试（`/providers/{name}/connectivity`）
- **不支持 ExtensionPoint 自定义提供商**：所有提供商内置于本插件，以确保一致的行为和安全性
- **无 Reconciler**：提供商状态仅通过手动连通性测试管理
- **不支持图像生成**：第一阶段仅覆盖对话和向量嵌入

## 能力

### 新增能力

- `ai-provider-config`：基于 Extension 的 AI 提供商配置管理（创建、读取、更新、删除、测试连通性）
- `ai-model-service`：供其他插件调用 AI 能力的编程 API（对话、流式对话、向量嵌入），通过包装的服务接口
- `console-model-management`：用于管理 AI 提供商配置和模型定义的 Vue Console UI

### 修改的能力

- （无 —— 这是一个新插件，没有现有能力需要修改）

## 影响

- **新的插件依赖**：消费者插件（如 plugin-ai-assistant）将在其 `plugin.yaml` 中声明 `pluginDependencies: ai-foundation: 1.*`
- **新的编译依赖**：消费者插件在其构建中添加 `compileOnly 'run.halo.aifoundation:api:x.x.x'`
- **plugin-ai-assistant 的迁移路径**：未来阶段将把 ai-assistant 迁移为依赖 ai-foundation，而非其自己的提供商实现
- **Spring AI 2.0.0-M2**：插件打包 Spring AI 库；消费者插件不需要 Spring AI 依赖
- **Halo 2.23+**：最低支持的 Halo 版本
