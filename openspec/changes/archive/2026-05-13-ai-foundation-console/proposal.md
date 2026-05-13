## 背景

`ai-foundation` 的 backend foundation change 将先交付 `AiProvider` / `AiModel`、服务端校验、provider 适配、模型发现、连通性测试和 `test-chat` 调试接口，但不会在同一个 change 中完成完整的 Console workspace。为了降低范围、便于评审和分阶段交付，需要将 Console UI 独立成后续 change。

## 变更内容

- 新增 `ui/` 模块，交付 Halo Console 集成页面
- 基于现有 `AiProvider` / `AiModel` Extension API 与 debug endpoints 构建 provider-centric workspace
- 提供 provider 管理、模型管理、Secret 绑定、模型发现、连通性测试和测试对话入口
- 对内置 provider type 提供“选厂商 + 填密钥”的预设表单；仅 `openailike` 暴露自定义 `baseUrl`

## 能力

### 新增能力

- `console-model-management`：用于管理 AI 提供商配置和模型定义的 Vue Console UI，交互方式采用主从式 provider workspace

## 影响

- 依赖 `ai-foundation` backend foundation change 先提供 `AiProvider` / `AiModel`、模型发现、连通性测试和 `test-chat` 接口
- 后续评审可将后端正确性与 UI 体验分开进行
