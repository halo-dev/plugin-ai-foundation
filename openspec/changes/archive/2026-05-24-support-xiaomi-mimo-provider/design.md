## Context

插件的 provider type 系统已经把供应商身份、展示元数据和运行时行为集中在单个 Spring `@Component` 中。新增内置供应商时，优先通过一个 `AiProviderType` 实现接入，并让 provider-types API 自动暴露给控制台。

MiMo 的常规 API host 为 `https://api.xiaomimimo.com`，模型调用按 OpenAI-compatible 形态接入。结合当前 `AbstractAiProviderType` 的约定，默认 base URL 应保持为 host 根地址，由 `OpenAiApi` 的 completions path 和默认 discovery path 追加 `/v1/...`。

```
Admin config
    │
    ▼
AiProvider(providerType = "mimo")
    │
    ▼
XiaomiMiMoProvider
    │
    ├─ metadata ──► provider-types API ──► Console provider selector
    │
    └─ runtime  ──► OpenAiChatModel
                    baseUrl: https://api.xiaomimimo.com
                    path:    /v1/chat/completions
```

## Goals / Non-Goals

**Goals:**

- 注册一个内置 Xiaomi MiMo provider type。
- 默认只要求管理员填写 API Key；base URL 可保留默认值，也可手动覆盖。
- 使用当前 OpenAI-compatible chat adapter 构建 `ChatModel`。
- 让模型发现沿用默认 `GET {baseUrl}/v1/models` 行为。
- 暴露完整内置供应商元数据，满足 provider metadata spec。

**Non-Goals:**

- 不新增 Anthropic-compatible adapter，也不接入 Claude Code 专用端点。
- 不新增 embedding adapter；当前只声明 `OPENAI_CHAT`。
- 不为 MiMo 单独设计前端表单或权限模型。

## Decisions

**1. providerType 使用 `mimo`**

- 与 MiMo 模型 ID 前缀和常见供应商标识保持一致。
- 避免把 providerType 写得过长，展示层通过 `displayName = "Xiaomi MiMo"` 呈现完整品牌。

**2. 默认 base URL 使用 `https://api.xiaomimimo.com`**

- 当前 OpenAI-compatible providers 的实现会把 completions path 配成 `/v1/chat/completions`。
- 如果默认 base URL 带 `/v1`，会和现有 path 约定叠加，增加错误配置风险。
- 管理员仍可通过 `AiProvider.spec.baseUrl` 覆盖默认地址。

**3. 只声明 chat adapter**

- 已有 MiMo 常用模型包括 `mimo-v2.5`、`mimo-v2.5-pro`、`mimo-v2-flash`、`mimo-v2-omni`，都属于语言/多模态对话模型范畴。
- 当前需求是模型供应商接入；embedding 兼容性没有足够确定性，不应在 provider metadata 中提前承诺。
- 因此 `getSupportedAdapterTypes()` 返回 `List.of(AdapterType.OPENAI_CHAT)`，`buildEmbeddingModel()` 继续使用基类默认 `null`。

**4. 不为模型列表做静态预置**

- 该插件已有远端 discovery 和手动创建模型的流程。
- MiMo 模型迭代较快，硬编码模型列表容易过期。
- 默认 discovery 使用 `/v1/models`，若远端不可用，管理员仍可手动创建模型 ID。

## Risks / Trade-offs

- **[风险]** MiMo 的 OpenAI-compatible 响应可能存在细节差异。
  - **缓解**：复用现有 `OpenAiChatModel` 适配路径；实现后用真实 provider-types 和可用 API Key 做 smoke test。
- **[风险]** `/v1/models` 发现接口可能不返回完整模型能力。
  - **缓解**：发现结果按现有规则低置信度推断；管理员可以手动调整模型元数据。
- **[权衡]** 暂不支持 Claude Code 专用端点。
  - 当前插件没有 Anthropic-compatible adapter，强行纳入会扩大范围并影响现有 provider 抽象。

## Migration Plan

无需迁移。该变更是纯新增 provider type，不影响已有 provider 配置或模型资源。

## Open Questions

- 是否要在实现阶段为 MiMo 增加一个 provider-specific 单元测试类，覆盖元数据和 `buildChatModel()` 构建行为？建议增加，风险低、回归价值高。
