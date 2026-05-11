## 背景

Halo CMS 插件生态系统目前缺乏集中式的 AI 基础设施。现有的 `plugin-ai-assistant` 实现了自己的提供商配置（OpenAI、DeepSeek、硅基流动等）和 AI 客户端管理。任何其他需要 AI 能力的插件都必须重复这项工作，迫使用户反复重新配置 API 密钥。

本插件（`plugin-ai-foundation`）旨在成为集中式 AI 基础设施基座：
1. 将 AI 提供商配置存储为 Halo Extension
2. 管理所有支持提供商的 AI 客户端生命周期
3. 通过发布的 `api/` 模块暴露干净的 Java SDK
4. 提供用于配置管理的 Console UI

参考：
- `plugin-ai-assistant`（`../plugin-ai-assistant/`）：要迁移的提供商实现来源
- `plugin-app-store`（`../plugin-app-store/`）：带 Maven 发布 API 的多模块 Gradle 模式
- `plugin-feed`（`../plugin-feed/`）：多模块 Gradle 模式（`api`、`app`、`ui`）

## 目标 / 非目标

**目标：**
- 提供统一的 AI 模型提供商配置入口
- 提供统一的 AI 模型定义管理（从已配置的提供商中添加模型）
- 暴露包装的 Java API（`AiModelService`），其他插件无需依赖 Spring AI 即可消费
- 支持 8 个以上提供商的对话和向量嵌入能力
- 提供 Vue Console 页面，用于提供商增删改查、模型管理和连通性测试
- 使用 Extension（`AiProvider`、`AiModel`）替代 settings.yaml 进行配置持久化

**非目标：**
- 图像生成（超出第一阶段范围）
- 全局代理配置（每个提供商自行配置代理）
- 自动协调或健康检查 Reconciler（仅手动连通性测试）
- 第三方提供商的 ExtensionPoint（所有提供商内置）
- 第一阶段迁移现有 ai-assistant 配置

## 决策

### 1. 基于 Extension 的配置替代 settings.yaml

**决策：** 使用自定义扩展（`AiProvider`，GVK：`aifoundation.halo.run/v1alpha1`）存储提供商配置。

**理由：**
- Extension 通过 Halo 通用 API 提供完整的增删改查，支持灵活的 Vue UI 管理
- settings.yaml 表单是静态的，难以动态添加/删除提供商
- 其他插件可以通过扩展事件监听 `AiProvider` 变更
- 符合 Halo 以扩展为中心的架构

**考虑的替代方案：** 使用 FormKit 的 settings.yaml —— 被拒绝，因为它不支持动态提供商实例（运行时添加/删除提供商）。

### 2. 包装 API 类型替代暴露 Spring AI

**决策：** `api/` 模块定义自己的类型（`ChatRequest`、`ChatResponse`、`Message`、`ChatChunk`、`EmbeddingResponse`），不暴露 Spring AI 类。

**理由：**
- 消费者插件无需添加 Spring AI 依赖
- API 稳定性与 Spring AI 版本升级解耦
- 更简单的 API 界面，聚焦常见用例

**权衡：** 一些高级 Spring AI 功能（函数调用、Advisor、自定义 ChatOptions）无法直接访问。需要高级功能的消费者仍可自行添加 Spring AI。

### 3. 多模块 Gradle 结构

**决策：** 三个模块：`api/`、`app/`、`ui/`。

**理由：**
- `api/` 作为轻量库发布到 Maven Central
- `app/` 包含插件运行时和提供商实现
- `ui/` 独立构建 Console 前端
- 遵循 `plugin-feed`（`api`/`app`/`ui`）和 `plugin-app-store`（`api`/`appstore`/`console`）的既定模式

### 4. 双 Extension 设计（AiProvider + AiModel）

**决策：** 采用两个 Extension 分别管理提供商配置和模型定义。消费者调用 `AiModelService.chat("${providerName}/${modelId}", prompt)`，通过 `providerName/modelId` 格式定位模型。

**理由：**
- 模型作为一等公民，独立生命周期，支持统一查询和管理
- `AiModel` 绑定到 `AiProvider`，界面展示为 `(提供商 / 模型)` 如 `(OpenAI / GPT-4o)`
- 调用简洁直观：`AiModelService.chat("aihubmix/claude-3.5", "你好")`
- 未来可扩展：给模型加 `defaultTemperature`、`capabilities` 等字段
- 从 Extension 配置到提供商客户端的直接映射

**`AiProvider`**：存储提供商配置（API 密钥、代理等）
**`AiModel`**：存储模型定义（providerName 引用、modelId、displayName）

**考虑的替代方案：** 单 Extension `AiProvider` 内嵌 `models` 列表 —— 被拒绝，因为模型多了之后单个体积过大，且无法独立查询和扩展。

### 5. 按提供商代理支持

**决策：** 每个 `AiProvider` Extension 的 `config` 映射可以包含 `proxyHost` 和 `proxyPort`。

**理由：**
- 不同提供商可能需要不同代理（如国内 vs 国际）
- 避免可能在提供商之间冲突的全局代理状态
- 匹配 ai-assistant 中现有的 `ProxyWebClient` 模式，但按提供商限定范围

### 6. 第一阶段无 Reconciler

**决策：** 提供商状态（`phase`、`message`）仅通过手动连通性测试更新。

**理由：**
- Reconciler 需要定期后台验证，增加复杂度
- 手动测试对初始用户体验足够
- 可稍后添加，不会破坏 API 变更

## 风险 / 权衡

| 风险 | 缓解措施 |
|------|---------|
| **Spring AI 版本锁定** —— Foundation 固定使用 Spring AI 2.0.0-M2；未来升级可能破坏间接依赖 Spring AI 的消费者插件 | API 模块包装所有类型；消费者永远看不到 Spring AI 类。Spring AI 仅在 `app/` 模块中。 |
| **提供商 API 漂移** —— 厂商 API 变更，破坏模型列表或连通性测试 | 提供商适配器是隔离的；只需更新受影响的适配器。模型列表是尽力而为（可回退到手动输入）。 |
| **Extension 模式演进** —— 添加新的提供商特定配置字段可能需要 Extension 版本升级 | 使用 `Map<String, String> config` 避免新提供商的模式变更。 |
| **类加载器隔离** —— 其他插件需要在运行时访问 `api/` 类 | 遵循 Halo 的 `pluginDependencies` 机制。API jar 在父插件的类加载器中。 |
| **迁移复杂度** —— 将 ai-assistant 从其自己的提供商层移出并非易事 | 第一阶段聚焦构建基础。迁移是单独的阶段，有专门的规划。 |

## 迁移计划

**第一阶段（本次变更）：** 构建包含所有提供商、API 模块和 Console UI 的 ai-foundation 插件。

**第二阶段（未来）：** 将 `plugin-ai-assistant` 迁移为依赖 `ai-foundation`：
- 从 ai-assistant 中移除提供商实现和 settings.yaml 提供商配置
- 添加 `compileOnly 'run.halo.aifoundation:api:x.x.x'` 和 `pluginDependencies: ai-foundation: 1.*`
- 更新 ai-assistant 的编辑器/摘要/RAG 服务以使用 `AiModelService`
- 将现有用户配置从 ai-assistant settings 迁移到 `AiProvider` 和 `AiModel` Extension

## 待解决问题

- `AiProvider` Extension 是否应该包含 `priority` 或 `default` 标志用于回退行为？
- `embed()` 是否应该支持批量大小限制或对大输入列表的自动分块？
