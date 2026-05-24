## Why

Xiaomi MiMo 是小米推出的大模型服务，提供 MiMo V2.5、MiMo V2.5 Pro、MiMo V2 Flash、MiMo V2 Omni 等对话模型。当前插件只能通过通用 OpenAI Compatible provider 手动配置 MiMo，管理员需要自己填写 API 地址，且控制台无法以独立品牌展示该供应商。

## What Changes

- 新增 Xiaomi MiMo 内置 provider type，建议 `providerType = "mimo"`。
- 使用默认 base URL `https://api.xiaomimimo.com`，通过现有 OpenAI-compatible chat adapter 调用 `/v1/chat/completions`。
- 在 provider-types API 中暴露 MiMo 的展示名称、描述、图标、官网、文档地址、默认 base URL 和支持的 adapter types。
- 复用现有动态 provider type 元数据机制，前端不新增硬编码供应商列表。
- 暂不声明 embedding 支持，直到确认 MiMo 的 embedding API 与当前 OpenAI embedding adapter 完全兼容。

## Capabilities

### New Capabilities

- `xiaomi-mimo-provider`: Xiaomi MiMo 内置 AI 供应商支持，包含 provider 注册、元数据暴露、chat model 构建和模型发现行为。

### Modified Capabilities

- 无现有 spec 的行为变更需求。

## Impact

- **后端**: 新增 `XiaomiMiMoProvider`，继承 `AbstractAiProviderType` 并复用 Spring AI `OpenAiChatModel`。
- **资源**: 使用 `/plugins/ai-foundation/assets/static/brands/xiaomimimo.png` 作为品牌图标路径。
- **API**: provider-types 列表自动包含 MiMo，无需新增或修改 REST endpoint。
- **依赖**: 无新增外部依赖。
- **前端**: 后端元数据驱动 provider 下拉和展示，常规控制台 UI 无需改动。

## Non-Goals

- 不支持 Anthropic-compatible / Claude Code 专用端点。
- 不实现 MiMo embedding、TTS、语音、图像、视频等非对话能力。
- 不硬编码 MiMo 模型列表；模型仍通过远端发现或管理员手动创建。
- 不改变现有 OpenAI Compatible provider 的行为。
