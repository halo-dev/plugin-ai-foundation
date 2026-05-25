## Why

Gitee 模力方舟提供兼容 OpenAI 风格的 Serverless API，适合接入 AI Foundation 现有的 OpenAI-compatible provider 体系。支持它可以让管理员直接配置模力方舟访问令牌和模型 ID，供 Halo 内其他插件复用对话模型能力。

## What Changes

- 新增内置 `Gitee 模力方舟` provider type，默认 base URL 为 `https://ai.gitee.com`。
- 通过现有 Spring AI OpenAI chat adapter 调用模力方舟 `/v1/chat/completions`。
- 在 provider-types API 中暴露模力方舟元数据，包括展示名、图标、官网、文档地址、默认 base URL 和支持的 adapter types。
- 复用现有 OpenAI-compatible model discovery 流程，按默认 `/v1/models` 约定发现可用模型；如果远端不支持模型列表，现有手动模型配置能力仍可使用。
- 增加后端 provider 元数据、ChatModel 构建、模型发现契约和品牌图标可用性的测试覆盖。

### Non-Goals

- 不新增或修改前端硬编码 provider 列表；前端继续从 provider-types API 获取 provider 元数据。
- 不修改公开 Java API、AiProvider/AiModel schema 或 TypeScript generated client。
- 不声明模力方舟 embedding 支持，除非后续确认其 embedding endpoint 与当前 OpenAI embedding adapter 契约兼容。
- 不实现模力方舟自定义参数、专属模型目录同步或账号资源包管理。

## Capabilities

### New Capabilities

- `gitee-moark-provider`: Defines Gitee 模力方舟 provider registration, metadata, OpenAI-compatible chat model construction, model discovery expectations, and icon asset availability.

### Modified Capabilities

- None.

## Impact

- Backend only: add a provider component under `app/src/main/java/run/halo/aifoundation/provider/`.
- Static assets: add a Gitee 模力方舟 brand icon under `app/src/main/resources/static/brands/`.
- Tests: add focused provider and provider-types endpoint assertions.
- APIs: provider-types response gains one additional built-in provider entry; no endpoint shape changes.
- Dependencies: no new dependency expected.
