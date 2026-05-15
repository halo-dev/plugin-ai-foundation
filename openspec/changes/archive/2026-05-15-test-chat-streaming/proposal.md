## Why

TestChatModal 的"测试对话"功能当前调用非流式 endpoint，用户需等待大模型生成完整回复后才能看到任何内容。对于长回复（如代码生成、文章续写），等待时间可达数十秒，体验极差。流式输出（SSE）能让用户在第一个 token 生成后立即看到内容，显著改善感知性能。

## What Changes

- **新增 SSE 流式 endpoint** `POST /models/{name}/test-chat/stream`，使用 `LanguageModel.streamChat()` 返回 `Flux<ChatChunk>` 作为 Server-Sent Events
- **替换前端 TestChatModal** 为非流式消费方式：使用 `fetch()` + `ReadableStream` 解析 SSE，逐块渲染文本内容
- **移除原有非流式 `test-chat` endpoint** — 该 endpoint 仅被 TestChatModal 使用，无其他消费者，直接替换避免维护两套接口

## Capabilities

### New Capabilities
- `test-chat-streaming`: 模型测试对话的流式输出能力，后端 SSE endpoint + 前端渐进渲染

### Modified Capabilities
- （无 — 此为纯实现层改动，不涉及现有 spec 的需求变更）

## Impact

- `ModelConsoleEndpoint`: 新增 `testChatStream` handler，移除 `testChat` handler
- `TestChatModal.vue`: 改用 `fetch()` 消费 SSE，`result` 从 `ref('')` 改为响应式追加模式
- `LanguageModel` / `ChatChunk`: 已有 `streamChat()` 接口和 `ChatChunk` DTO，无需修改
- OpenAPI 生成: 前端 API client 会在构建时自动更新
