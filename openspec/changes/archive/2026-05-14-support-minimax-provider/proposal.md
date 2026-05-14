## Why

MiniMax 是国内头部大模型厂商之一，提供 M2.7、M2.5、M2.1 等高性能对话模型，支持 204,800 tokens 超长上下文。用户希望在该插件中使用 MiniMax 的模型能力，因此需要添加 MiniMax 作为内置 AI 提供商。

## What Changes

- 新增 `MiniMaxProvider` 组件，将 MiniMax 注册为内置 AI 提供商类型
- 支持通过 OpenAI 兼容 API 调用 MiniMax 的对话模型（chat completions）
- 添加 MiniMax 品牌图标资源
- 在 provider-types API 中暴露 MiniMax 的元数据（displayName、defaultBaseUrl、websiteUrl 等）
- 暂不支持 MiniMax Embedding（其 Embedding API 为非 OpenAI 兼容的自定义格式）

## Capabilities

### New Capabilities
- `minimax-provider`: MiniMax 内置 AI 提供商支持，包含 provider 注册、chat model 构建、元数据暴露

### Modified Capabilities
- 无现有 spec 的行为变更需求

## Impact

- **后端**: 新增 `app/src/main/java/run/halo/aifoundation/provider/MiniMaxProvider.java`
- **资源**: 新增 `ui/src/assets/static/brands/minimax.png`（或 SVG）图标
- **API**: provider-types 列表自动包含 MiniMax，无需手动注册
- **依赖**: 无新增外部依赖，复用 Spring AI OpenAiChatModel
- **前端**: 无需修改，provider 元数据通过 API 动态获取
