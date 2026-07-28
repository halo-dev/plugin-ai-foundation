# SDK UI：Stream Protocol

简体中文 | [English](../../en/sdk-ui/stream-protocol.md)

Halo UI Message stream 使用 Server-Sent Events。每个 `data:` frame 是一个 JSON
`UIMessageChunk`，最后以 `[DONE]` 结束。

## HTTP

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
Cache-Control: no-cache
```

示例：

```text
data: {"type":"start","messageId":"assistant-1"}

data: {"type":"start-step","stepIndex":0}

data: {"type":"text-start","id":"text-1"}

data: {"type":"text-delta","id":"text-1","delta":"Halo"}

data: {"type":"text-end","id":"text-1"}

data: {"type":"finish-step","stepIndex":0,"finishReason":"stop"}

data: {"type":"finish","finishReason":"stop"}

data: [DONE]
```

空行分隔 SSE frame。`[DONE]` 不是 JSON chunk，reader 接受并忽略它。

## 生命周期 Chunk

| type          | 关键字段                          | 聚合行为                     |
| ------------- | --------------------------------- | ---------------------------- |
| `start`       | `messageId?`、`messageMetadata?`  | 初始化 assistant message     |
| `start-step`  | `stepIndex?`                      | 追加无字段 `step-start` part |
| `finish-step` | reason、usage、warnings、metadata | 更新步骤终态，不进入 parts   |
| `finish`      | reason、usage、message metadata   | 更新整体终态                 |
| `error`       | `errorText`                       | 写入 `terminal.errorText`    |
| `abort`       | 无                                | 写入 `terminal.aborted`      |

持久化的 `StepStartPart` 不保存 `stepIndex`。它在 `parts` 中的位置用于恢复步骤边界。

## 文本

```text
text-start(id)
text-delta(id, delta)*
text-end(id)
```

同一个 `id` 的 delta 按顺序拼接为一个 `TextPart`。delta 之前必须有对应 start。

## 推理

```text
reasoning-start(id)
reasoning-delta(id, delta, providerMetadata?)*
reasoning-end(id)
```

聚合为 `ReasoningPart`。Provider metadata 可能包含继续推理历史所需的不透明状态，保存消息时
不要随意删除；是否回传由 Java 后端根据模型 capability 决定。

## Source

`source-url`：

```json
{
    "type": "source-url",
    "sourceId": "post-1",
    "url": "https://example.com/post-1",
    "title": "Halo"
}
```

`source-document`：

```json
{
    "type": "source-document",
    "sourceId": "doc-1",
    "mediaType": "text/markdown",
    "title": "Guide",
    "filename": "guide.md"
}
```

source 会进入最终 `parts`。

## File

URL：

```json
{
    "type": "file",
    "fileId": "image-1",
    "url": "https://example.com/image.png",
    "mediaType": "image/png",
    "title": "image.png"
}
```

base64：

```json
{
    "type": "file",
    "fileId": "image-1",
    "data": "iVBORw0KGgo...",
    "mediaType": "image/png",
    "title": "image.png"
}
```

file 会进入最终 `parts`，资源上传、下载和存储由业务层管理。

## Data

```json
{
    "type": "data-rag-status",
    "id": "rag-status-1",
    "name": "rag-status",
    "data": {
        "stage": "retrieving"
    },
    "transient": true
}
```

- `transient: false` 或省略：聚合为 `DataPart`。
- `transient: true`：触发 `onData`，不进入最终 parts。
- 同一 `id` 的持久化 data 更新会替换旧值。

## Message Metadata

```json
{
    "type": "message-metadata",
    "messageMetadata": {
        "modelName": "default-chat"
    }
}
```

它更新 `UIMessage.metadata`，不生成 part。

## 工具输入

增量路径：

```text
tool-input-start(toolCallId, toolName)
tool-input-delta(toolCallId, inputTextDelta)*
tool-input-available(toolCallId, toolName, input)
```

final-only 路径：

```text
tool-input-available(toolCallId, toolName, input)
```

错误：

```json
{
    "type": "tool-input-error",
    "toolCallId": "call_1",
    "toolName": "search",
    "errorText": "input does not match schema"
}
```

注意：

- delta 不包含 `toolName`，必须在此前收到 start。
- 重复 start 会重置该 call 的私有增量状态。
- partial JSON 只是 best-effort。
- available input 是权威最终值。
- final-only 是正常行为，不能按 Provider 名称假设一定存在 delta。

## 工具结果

成功：

```json
{
    "type": "tool-output-available",
    "toolCallId": "call_1",
    "toolName": "search",
    "output": {
        "title": "Halo"
    }
}
```

失败：

```json
{
    "type": "tool-output-error",
    "toolCallId": "call_1",
    "toolName": "search",
    "errorText": "service unavailable"
}
```

它们更新同一个 `tool-*` part，不创建新的消息。

## 工具审批

请求：

```json
{
    "type": "tool-approval-request",
    "approvalId": "approval_call_1",
    "toolCallId": "call_1",
    "toolName": "delete_post",
    "input": {
        "postName": "welcome"
    }
}
```

响应：

```json
{
    "type": "tool-approval-response",
    "approvalId": "approval_call_1",
    "toolCallId": "call_1",
    "toolName": "delete_post",
    "approved": false,
    "reason": "用户取消"
}
```

审批请求与响应聚合到同一个动态工具 part。

## 聚合结果

wire chunk：

```text
start-step
reasoning-start / delta / end
text-start / delta / end
tool-input-available
tool-output-available
```

可能聚合为：

```json
{
    "id": "assistant-1",
    "role": "assistant",
    "parts": [
        { "type": "step-start" },
        { "type": "reasoning", "id": "reasoning-1", "text": "..." },
        { "type": "text", "id": "text-1", "text": "..." },
        {
            "type": "tool-search",
            "toolCallId": "call_1",
            "toolName": "search",
            "state": "output-available",
            "input": { "query": "Halo" },
            "output": { "title": "Halo" }
        }
    ]
}
```

wire 使用 `tool-input-*` / `tool-output-*` / `tool-approval-*` 表达事件；持久化消息使用动态
`tool-*` part 表达最终 UI 状态。

## 校验规则

SDK 会拒绝：

- 未知 chunk type。
- 必填字段缺失或类型错误。
- text / reasoning delta 没有对应 start。
- tool input delta 先于 tool input start。
- 不符合已配置 runtime schema 的 metadata 或 data。
- header 存在但协议版本不是 `v1`。

已收到合法 chunk 后发生网络中断，reader 会标记 `disconnected` 并保留部分 message；纯协议或
schema 错误标记为 `error`。协议内的 `error` / `abort` chunk 写入 terminal，直接使用 reader
时应检查 `terminal.errorText` / `terminal.aborted`；`Chat` 会把错误终态转成 chat error。

更高层用法见 [Transport 与读取消息流](./transport-and-reading-streams.md)。
