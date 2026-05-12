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
- **Vercel AI SDK**（`../ai/`）：provider 抽象架构、能力分离接口（`LanguageModelV4` / `EmbeddingModelV4`）、流式 Part 标准化、`providerOptions` 透传机制、错误类型体系的设计参考
- **Cherry Studio**（`../cherry-studio/`）：provider workspace 交互、模型能力标签、模型分组与批量管理体验的设计参考

## 目标 / 非目标

**目标：**
- 提供统一的 AI 模型提供商配置入口
- 提供统一的 AI 模型定义管理（从已配置的提供商中添加模型）
- 暴露包装的 Java API（`AiModelService`），其他插件无需依赖 Spring AI 即可消费
- 支持 8 个以上提供商接入，并按 provider 实际能力暴露对话和向量嵌入能力
- 提供 Vue Console 页面，用于提供商增删改查、模型管理和连通性测试
- 使用 Extension（`AiProvider`、`AiModel`）替代 settings.yaml 进行配置持久化
- 在不放弃 Halo Extension 资源建模的前提下，提供接近 Cherry Studio 的厂商/模型配置体验

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

**决策：** 采用两个 Extension 分别管理提供商配置和模型定义。消费者通过 `${providerResourceName}/${modelId}` 格式定位模型，其中 `providerResourceName` 对应 `AiProvider.metadata.name`，而不是 `providerType`；Console UI 则按 provider 聚合展示其配置和关联模型，形成 Cherry Studio 风格的 provider workspace。

**理由：**
- 模型作为一等公民，独立生命周期，支持统一查询和管理
- `AiModel` 绑定到 `AiProvider`，底层引用使用 provider 实例资源名，界面展示为 `(提供商 / 模型)` 如 `(OpenAI 官方 / GPT-4o)`
- 调用简洁直观，同时支持同类型 provider 多实例：`AiModelService.chat("openai-official/gpt-4o", "你好")`
- 可以在 `AiModel` 上承载 `group`、`capabilities`、`endpointType`、`supportedTextDelta` 等仅用于管理与选择的元数据
- 从 Extension 配置到提供商客户端的直接映射
- 不把 `models` 内嵌到 `AiProvider`，仍可通过 provider 聚合查询构建 Cherry Studio 式右侧详情页

**`AiProvider`**：存储提供商实例配置（providerType、displayName、baseUrl、apiKeySecretName、enabled、proxy、高级 config 等）
**`AiModel`**：存储模型定义与管理元数据（providerName 引用 `AiProvider.metadata.name`，表示 provider 实例资源名而非 providerType；包含 modelId、displayName、group、capabilities、endpointType、supportedTextDelta、enabled）

**考虑的替代方案：** 单 Extension `AiProvider` 内嵌 `models` 列表 —— 被拒绝，因为模型多了之后单个体积过大，且无法独立查询和扩展。

### 5. 按提供商代理支持

**决策：** `AiProvider.Spec` 同时采用结构化字段和扩展配置：通用连接字段（如 `baseUrl`、`enabled`）使用显式字段，敏感凭据通过 `apiKeySecretName` 引用单个 Halo Secret，provider-specific 高级选项保留在 `config` 映射中；代理配置可通过结构化字段或 `config` 扩展承载。

**理由：**
- 不同提供商可能需要不同代理（如国内 vs 国际）
- 避免可能在提供商之间冲突的全局代理状态
- 匹配 ai-assistant 中现有的 `ProxyWebClient` 模式，但按提供商限定范围
- 仅用 `Map<String, String> config` 难以支撑 Cherry Studio 风格的表单交互、字段校验和敏感信息处理
- 将常用连接信息结构化后，Console 可以更稳定地实现输入、脱敏、检测和帮助文案
- Halo Secret 比直接把 API Key 放进 Extension spec 更符合敏感信息管理需求
- 内置厂商预设（如 `aihubmix`、`siliconflow`、`openai`、`deepseek`）应优先提供“选厂商 + 填密钥”的体验，而不是暴露底层 `baseUrl`

**内置厂商预设与自定义 provider 的边界：**
- `aihubmix`、`siliconflow`、`openai`、`deepseek`、`doubao`、`ernie`、`zhipuai` 等内置 provider type 使用插件内置的默认 `baseUrl` / 请求头策略
- `openailike` 作为自定义 OpenAI-compatible provider 的兜底类型，要求用户显式填写 `baseUrl`
- `baseUrl` 字段在资源模型中保留，但对内置 provider type 可由服务端填充默认值，或在 Console 中默认隐藏/只读

**密钥策略：**
- 第一阶段仅支持单个 `apiKeySecretName`
- 运行时始终从该 Secret 解析凭据，不支持多 Secret 轮换或回退
- 连通性测试按 provider 粒度执行，不返回逐 key 检测结果
- 后续如需兼容 Cherry Studio 风格的多 key，可约定在同一个 Secret value 中使用逗号分隔多个 key，由 provider adapter 自行解析

### 6. 模型元数据增厚以支撑管理体验

**决策：** `AiModel.Spec` 除基本标识外，第一阶段增加 `group`、`capabilities`、`endpointType`、`supportedTextDelta`、`enabled` 等字段，用于表达模型分组、能力标签和协议兼容性。

**理由：**
- Cherry Studio 的模型管理体验依赖分组、能力标签和端点类型来支持搜索、筛选和编辑
- provider API 返回的模型数据并不总是完整或稳定，需要本地元数据做补充与覆盖
- 这些字段优先服务于 Console 和模型选择体验，不要求全部暴露到公共 Java API

**约束：**
- `(providerName, modelId)` 必须唯一
- `providerName` 必须引用现有 `AiProvider.metadata.name`
- 删除仍被 `AiModel` 引用的 `AiProvider` 时应阻止删除，避免隐式级联删除模型
- 所有唯一性、引用完整性和删除保护约束以服务端校验为准，UI 仅做前置提示

### 7. 第一阶段无 Reconciler

**决策：** 提供商状态（`phase`、`message`）仅通过手动连通性测试更新。

**理由：**
- Reconciler 需要定期后台验证，增加复杂度
- 手动测试对初始用户体验足够
- 可稍后添加，不会破坏 API 变更

### 8. Cherry Studio 风格的 provider workspace

**决策：** Console UI 参考 Cherry Studio，采用左侧 provider 列表、右侧 provider 详情与模型列表的工作区布局，而不是将 provider 和 model 完全拆成两个割裂的管理页面。

**理由：**
- provider 配置和其关联模型本质上是同一个管理上下文
- 右侧详情区可以同时承载 API 密钥、API 地址、连通性检测、模型搜索、批量导入、模型编辑等操作
- 存储层依然保持 `AiProvider` / `AiModel` 分离，不牺牲 Halo Extension 的可查询性

### 9. 能力分离的 API 接口设计

**决策：** `api/` 模块将 chat 和 embedding 能力拆分为独立的接口 —— `LanguageModel` 和 `EmbeddingModel`，`AiModelService` 作为 Registry/Factory 返回具体能力接口。

**理由：**
- 借鉴 Vercel AI SDK 的 `LanguageModelV4` / `EmbeddingModelV4` 分离设计
- 消费者插件只依赖所需能力的接口，不需要引入无关的类型
- 为第二阶段扩展图像生成（`ImageModel`）、语音合成预留清晰的接口位置
- 每个接口可独立演进版本，互不干扰

**调用方式：**
```java
// 消费插件获取能力接口，modelRef 采用 provider 实例资源名 / modelId
LanguageModel lm = aiModelService.languageModel("openai-official/gpt-4o");
Mono<String> result = lm.chat("Hello");

EmbeddingModel em = aiModelService.embeddingModel("openai-official/text-embedding-3-small");
Mono<EmbeddingResponse> result = em.embed(List.of("text1", "text2"));
```

### 10. 扩展 ChatChunk 流式数据结构

**决策：** `ChatChunk` 不仅包含 `content` 和 `last` 标记，还增加 `type`、`finishReason` 和 `usage` 字段。

**理由：**
- 借鉴 Vercel AI SDK 的 `LanguageModelV4StreamPart` 标准化设计
- 未来支持 reasoning、tool calling 时不需要改变 API 形状
- `usage` 字段（promptTokens、completionTokens）对计费/监控场景必要
- `finishReason`（stop、length、error）让消费者知道对话终止原因

**结构：**
```java
public class ChatChunk {
    ChunkType type;        // TEXT, REASONING, TOOL_CALL, ERROR, FINISH
    String content;
    boolean last;
    String finishReason;   // stop, length, error, null
    Usage usage;           // promptTokens, completionTokens
}
```

### 11. Provider Options 透传机制

**决策：** `ChatRequest` 增加 `Map<String, Object> providerOptions` 字段，用于透传 provider-specific 配置。

**理由：**
- 借鉴 Vercel AI SDK 的 `providerOptions` 命名空间设计
- 通用 API 不需要暴露所有 provider 特有参数（如 OpenAI 的 `logitBias`、Anthropic 的 `cacheControl`）
- 消费者需要高级功能时可通过透传机制使用，不破坏 API 稳定性
- 每个 provider adapter 自行解析自己关心的 providerOptions 键

### 12. 错误类型体系

**决策：** `api/` 模块定义基于 `AiFoundationException` 的异常层次结构，替代通用的 `Mono.error()`。

**理由：**
- 借鉴 Vercel AI SDK 的 `AISDKError` 基类 + marker pattern
- 消费插件可以通过 `instanceof` 精确处理不同类型的错误（模型不存在 vs 提供商禁用 vs API 调用失败）
- 统一的错误基类便于日志收集和监控

**类型：**
```java
public class AiFoundationException extends RuntimeException { ... }
public class ModelNotFoundException extends AiFoundationException { ... }
public class ProviderDisabledException extends AiFoundationException { ... }
public class ProviderApiException extends AiFoundationException {
    int statusCode;
    String providerType;
}
```

## 风险 / 权衡

| 风险 | 缓解措施 |
|------|---------|
| **Spring AI 版本锁定** —— Foundation 固定使用 Spring AI 2.0.0-M2；未来升级可能破坏间接依赖 Spring AI 的消费者插件 | API 模块包装所有类型；消费者永远看不到 Spring AI 类。Spring AI 仅在 `app/` 模块中。 |
| **提供商 API 漂移** —— 厂商 API 变更，破坏模型列表或连通性测试 | 提供商适配器是隔离的；只需更新受影响的适配器。模型列表是尽力而为（可回退到手动输入）。 |
| **Extension 模式演进** —— 新提供商可能引入额外配置字段 | 通用连接字段结构化，敏感凭据通过 Halo Secret 引用，provider-specific 选项放入 `config`，在校验与扩展性之间折中。 |
| **类加载器隔离** —— 其他插件需要在运行时访问 `api/` 类 | 遵循 Halo 的 `pluginDependencies` 机制。API jar 在父插件的类加载器中。 |
| **迁移复杂度** —— 将 ai-assistant 从其自己的提供商层移出并非易事 | 第一阶段聚焦构建基础。迁移是单独的阶段，有专门的规划。 |
| **模型元数据漂移** —— 厂商返回的模型能力信息可能不完整或变更频繁 | 允许管理员在 `AiModel` 上编辑和覆盖能力标签、分组与端点类型。 |

## 迁移计划

**第一阶段（本次变更）：** 构建包含所有提供商、API 模块和 Console UI 的 ai-foundation 插件。

**第二阶段（未来）：** 将 `plugin-ai-assistant` 迁移为依赖 `ai-foundation`：
- 从 ai-assistant 中移除提供商实现和 settings.yaml 提供商配置
- 添加 `compileOnly 'run.halo.aifoundation:api:x.x.x'` 和 `pluginDependencies: ai-foundation: 1.*`
- 更新 ai-assistant 的编辑器/摘要/RAG 服务以使用 `AiModelService`
- 将现有用户配置从 ai-assistant settings 迁移到 `AiProvider` 和 `AiModel` Extension

## 待解决问题

- `AiProvider` Extension 是否应该包含 `priority` 或 `default` 标志用于回退行为？
  - **初步结论：** 第一阶段暂不引入。provider 回退逻辑增加了复杂度，待有明确 consumer 需求后再添加。可在 `AiProvider.Spec` 中预留 `defaultModel` 字段指向默认的 `AiModel`。
- `embed()` 是否应该支持批量大小限制或对大输入列表的自动分块？
  - **初步结论：** 借鉴 Vercel AI SDK 的 `embedMany` 设计，`EmbeddingModel` 接口暴露 `maxEmbeddingsPerCall()` 和 `supportsParallelCalls()` 属性。`AiModelServiceImpl` 内部实现自动分块和并行调用，消费者无需感知批量限制。
