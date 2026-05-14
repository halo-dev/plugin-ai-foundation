## Context

该插件通过 Provider Type 系统支持多种 AI 提供商。每个提供商是一个单独的 Spring 组件类，实现 `AiProviderType` 接口并继承 `AbstractAiProviderType`。目前已有 9 个内置提供商（OpenAI、DeepSeek、Kimi、Ollama 等）。

MiniMax 是国内主流大模型厂商，提供 OpenAI 兼容的对话 API。添加 MiniMax 支持意味着新增一个 provider 组件，复用现有的 `OpenAiChatModel` 基础设施即可，无需引入新依赖。

## Goals / Non-Goals

**Goals:**
- 将 MiniMax 注册为内置 AI 提供商类型（providerType = "minimax"）
- 支持通过 OpenAI 兼容 API 调用 MiniMax 对话模型
- 暴露 MiniMax 元数据（displayName、icon、websiteUrl、documentationUrl 等）
- 提供默认 base URL，用户只需填写 API Key 即可使用

**Non-Goals:**
- 支持 MiniMax Embedding（其 Embedding API 为非 OpenAI 兼容的自定义格式）
- 支持 MiniMax 的 TTS、Video、Image、Music 等非对话能力
- 支持 Anthropic 兼容协议
- 前端界面修改（provider 元数据通过 API 动态获取，前端无需改动）

## Decisions

**1. 使用 OpenAI 兼容 API，而非 Anthropic 兼容协议**
- 该插件所有现有提供商（除 Ollama 外）均基于 Spring AI 的 OpenAI 适配层
- MiniMax 的 OpenAI 兼容 API 完整支持 chat completions
- Anthropic 协议需要引入额外的适配器，增加复杂度且无收益

**2. 默认 base URL 使用国际版 `https://api.minimax.io`**
- 国际版是 MiniMax 推荐的标准接入点
- 用户可通过自定义 baseUrl 切换到中国版 `https://api.minimaxi.com`
- 与现有 `requiresBaseUrl() = false`、`isBuiltIn() = true` 的惯例一致

**3. 不支持 Embedding**
- MiniMax 的 Embedding API（`embo-01`）使用自定义请求/响应格式，与 OpenAI 的 `/v1/embeddings` 不兼容
- Spring AI 的 `OpenAiEmbeddingModel` 无法直接使用
- 实现自定义 embedding 适配需要额外工作量，且目前无明确需求
- `supportsEmbeddings() = false` 与 Kimi、DouBao 等现有提供商保持一致

**4. 复用现有 provider 图标约定**
- 图标路径：`/plugins/ai-foundation/assets/static/brands/minimax.png`
- 与现有 9 个提供商的图标存放路径保持一致

## Risks / Trade-offs

- **[风险]** MiniMax OpenAI 兼容 API 的响应格式可能与标准 OpenAI 有细微差异
  - **缓解**：基于 Spring AI OpenAiChatModel 构建，该模型已处理多种兼容变体；如出现问题可通过 `providerOptions` 透传参数调整
- **[风险]** MiniMax 模型命名（如 `MiniMax-M2.7`）可能需要精确匹配
  - **缓解**：模型 ID 由用户在创建 AiModel 时填写，非硬编码，用户可直接使用官方模型名称
- **[权衡]** 暂不支持 Embedding
  - 如需 Embedding 支持，未来需要实现自定义适配器或使用 MiniMax 的自定义 API 格式

## Migration Plan

无需迁移。该变更是纯新增功能，不影响现有提供商或配置。

## Open Questions

无
