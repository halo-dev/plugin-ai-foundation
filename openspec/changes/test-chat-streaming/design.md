## Context

当前 `ModelConsoleEndpoint` 提供 `POST /models/{name}/test-chat`，内部调用 `LanguageModel.chat(prompt)`（`Mono<String>`），前端 `TestChatModal.vue` 通过 `useMutation` 等待完整响应后一次性渲染。`LanguageModel` 接口早已定义 `streamChat(ChatRequest)` 返回 `Flux<ChatChunk>`，但测试对话功能未使用。

`ChatChunk` 结构已包含流式所需的全部信息：`type`（TEXT/REASONING/ERROR/FINISH）、`content`、`last`、`finishReason`。后端无需新增 DTO。

## Goals / Non-Goals

**Goals:**
- 新增 SSE endpoint，将 `streamChat()` 的 `Flux<ChatChunk>` 暴露为 Server-Sent Events
- 前端 `TestChatModal` 改为渐进渲染，首个 token 到达即刻显示
- 流式过程中出错时，前端展示错误信息并停止渲染

**Non-Goals:**
- 修改 `LanguageModel` 接口或 `ChatChunk` / `ChunkType` 定义
- 构建通用 SSE 基础设施供其他功能复用
- 保留原有非流式 endpoint（唯一消费者是 TestChatModal，直接替换）
- 支持除 `TEXT` 和 `ERROR`/`FINISH` 外的其他 chunk 类型（如 `REASONING`、`TOOL_CALL`）——当前 UI 仅渲染文本，后续可扩展

## Decisions

**Decision 1: 使用 `fetch()` + `ReadableStream` 而非 `EventSource`**
- `EventSource` 原生仅支持 GET，无法携带请求体（测试对话需要 POST + `prompt` 参数）。
- `fetch()` + `ReadableStream` 可自定义请求方法、headers、body，且能逐行读取 SSE 数据。
- 现代浏览器（Chrome 76+、Firefox 65+、Safari 14.1+）均支持 `ReadableStream`。

**Decision 2: 移除原有非流式 endpoint，不保留兼容**
- 经全文搜索，`POST /models/{name}/test-chat` 仅有 `TestChatModal.vue` 一处调用。
- 保留两套接口增加维护成本，且本插件尚未发布，无需考虑向后兼容。

**Decision 3: RouterFunction 中使用 `MediaType.TEXT_EVENT_STREAM` 配合 `Flux<ChatChunk>`**
- Spring WebFlux 的 `ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(flux, ChatChunk.class)` 会自动将每个 `ChatChunk` 序列化为 JSON 并包装为 SSE `data:` 行。
- 无需手动构造 `ServerSentEvent<?>` 对象，代码更简洁。

**Decision 4: 前端按行解析 SSE，过滤 `data:` 前缀后追加到结果字符串**
- 简单可靠，不引入额外 SSE 解析库。
- `ChatChunk` 中的 `type` 字段前端暂不消费（仅处理 `content`），但保留扩展空间。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| SSE 连接中途断开，前端处于无限 loading 状态 | `fetch()` 的 `.catch()` + `AbortController` 超时处理，断开时设置错误状态并停止 spinner |
| 后端 `streamChat()` 抛出异常，SSE 流异常终止 | `Flux.onErrorResume` 在 endpoint 层捕获，发出一个 `ChunkType.ERROR` 的 `ChatChunk` 后正常结束流，前端识别 `ERROR` 类型展示错误 |
| 某些网络代理/防火墙可能缓冲 SSE 响应 | 使用 `X-Accel-Buffering: no` header 禁用 Nginx 等代理的缓冲；本插件部署在 Halo 内部，Halo 默认不启用此类缓冲 |
| 用户快速连续点击"发送"导致多个并发流 | 前端在流进行中禁用发送按钮（利用 `isLoading` 状态），新的发送请求会中断上一个（通过复用 `AbortController`） |
