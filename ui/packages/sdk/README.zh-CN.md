# @halo-dev/ai-foundation-sdk

[English](./README.md) | 中文

`@halo-dev/ai-foundation-sdk` 是 Halo AI Foundation 面向浏览器和 Vue UI 的
stream SDK。它帮助前端发送聊天、补全和对象流请求，消费 Halo UIMessage SSE 响应，
把 stream chunk 归约为 UI message，并校验或持久化这些消息。

这个包不是后端 Provider SDK。模型选择、Provider 配置，以及从 UI message 转换到模型
message 的逻辑仍由 Halo AI Foundation 后端负责。

## 安装

```sh
pnpm add @halo-dev/ai-foundation-sdk
```

`vue` 3.5 是 peer dependency。这个包可以安全地在 SSR 和测试环境中导入，因为
`fetch` 等浏览器 API 只会在真正发起请求时读取。如果运行时没有内置 `fetch`，可以传入
自定义 `fetch`。

## Chat

在 Vue 组件中使用 `useChat`。它会管理 `UIMessage` 状态，并通过 chat transport 发送
`UIMessageChatRequest`。

```ts
import { DefaultChatTransport, useChat } from '@halo-dev/ai-foundation-sdk'

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '/apis/example.halo.run/v1alpha1/chat/stream',
  }),
  experimental_throttle: { intervalMs: 50 },
})

await chat.sendMessage({ text: '你好' })
```

`DefaultChatTransport` 会发送 JSON。最终 body 会合并 transport 级别的 body、单次调用
的 body，以及聊天请求字段：

```json
{
  "id": "conversation-1",
  "messages": [],
  "trigger": "submit-message",
  "messageId": null
}
```

stream endpoint 应返回 Server-Sent Events，其中每个 `data:` payload 都是一个 JSON
`UIMessageChunk`。最终的 `data: [DONE]` frame 会被接受并忽略。如果响应包含
`X-Halo-AI-UI-Message-Stream`，值必须是 `v1`。

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
```

`useChat` 返回 `messages`、`status`、`error` 和 `isLoading` 的只读 Vue ref，并提供
以下操作：

- `sendMessage({ text, files, parts, metadata })`
- `regenerate({ messageId })`
- `stop()`
- `setMessages(messages)`
- `clearError()`
- `addToolOutput(...)`
- `addToolApprovalResponse(...)`
- `rejectToolCall(...)`
- `isLastAssistantMessageToolComplete()`

如果 endpoint 返回普通文本而不是 Halo UIMessage SSE，可以使用 `TextStreamChatTransport`。
它会把文本增量包装成单个 assistant text part。

```ts
import { TextStreamChatTransport, useChat } from '@halo-dev/ai-foundation-sdk'

const chat = useChat({
  transport: new TextStreamChatTransport({ api: '/apis/example.halo.run/v1alpha1/chat/text' }),
})
```

`experimental_throttle` 只节流 Vue 可见的 `messages` 提交。stream 消费、reducer 更新、
`onData`、`onToolCall` 和终态刷新不会被延迟。`undefined`、`0` 和负数表示禁用节流。

## Runtime Schemas

`useChat`、`Chat` 和 `readUIMessageStream` 可以在运行时校验 streamed message metadata
和持久化的 `data-*` part。

```ts
const chat = useChat({
  id: 'conversation-1',
  transport,
  messageMetadataSchema: {
    safeParse: (value) =>
      value && typeof value === 'object' && !Array.isArray(value)
        ? { success: true, data: value as Record<string, unknown> }
        : { success: false, error: { message: 'metadata must be an object' } },
  },
  dataPartSchemas: {
    status: {
      '~standard': {
        validate: (value) =>
          typeof value === 'string'
            ? { value }
            : { issues: [{ message: 'status must be a string' }] },
      },
    },
  },
})
```

`messageMetadataSchema` 会在 metadata 每次更新后校验合并结果。`dataPartSchemas` 以 data
name 为 key，例如 `data-status` chunk 使用 `status` schema。支持的 schema 形态包括
JSON Schema 对象、`safeParse` / `parse` 对象，以及同步 Standard Schema adapter。
UI message stream 不支持异步 schema。

校验失败时，SDK 会抛出 `AIUISchemaValidationError`，设置 chat `error`，调用 `onError`，
并以 `isError: true` 结束 stream。

## 读取 UIMessage Streams

当你已经自己管理请求发送和消息状态，或者需要在非 Vue runtime 中消费 stream 时，可以使用
`readUIMessageStream`。

```ts
import { readUIMessageStream, type UIMessage } from '@halo-dev/ai-foundation-sdk'

const response = await fetch('/apis/example.halo.run/v1alpha1/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    id: 'conversation-1',
    messages,
    trigger: 'submit-message',
  }),
})

if (!response.ok) {
  throw new Error(await response.text())
}

const result = await readUIMessageStream({
  response,
  messageId: 'assistant-1',
  dataPartSchemas: {
    status: {
      safeParse: (value) =>
        typeof value === 'string'
          ? { success: true, data: value }
          : { success: false, error: { message: 'status must be a string' } },
    },
  },
  onChunk(chunk) {
    console.debug('raw chunk', chunk)
  },
  onMessage(message: UIMessage) {
    updateAssistantMessage(message)
  },
  onData(part) {
    console.debug('data', part.name, part.data)
  },
  onToolCall(part) {
    console.debug('tool call', part.toolName, part.input)
  },
})

if (result.status === 'disconnected') {
  savePartialMessage(result.message)
}
```

`readUIMessageStream` 可以读取 `AsyncIterable<UIMessageChunk>`、`ReadableStream<Uint8Array>`
或 `Response`。它不负责发起请求、不检查 `response.ok`、不解析非 stream 错误响应、
不重连、不 replay，也不会自动继续工具调用。`onChunk` 会收到 reducer 校验前的原始协议
chunk；`onMessage`、`onData` 和 `onToolCall` 只会在 reducer 接受 chunk 后触发。

## Completion

`useCompletion` 会发送 `{ prompt, ...body }`，并读取普通文本流。

```ts
import { useCompletion } from '@halo-dev/ai-foundation-sdk'

const completion = useCompletion({
  api: '/apis/example.halo.run/v1alpha1/completion/stream',
  body: { temperature: 0.2 },
})

await completion.complete('写一个标题')
```

返回对象包含 `completion`、`input`、`error`、`isLoading`、`complete`、`stop`、
`setCompletion`、`setInput`、`handleInputChange` 和 `handleSubmit`。

单次调用的请求参数会和已配置的 `headers`、`body`、`credentials` 合并。

```ts
await completion.complete('写一个标题', {
  body: { requestId: 'request-1' },
  headers: { 'X-Request-Id': 'request-1' },
  credentials: 'include',
})
```

## Object Streaming

`experimental_useObject` 会发送 `{ input, schema, output, ...body }`，并读取 JSON 文本流。
读取过程中会尽量解析部分 JSON 快照；stream 结束后，会解析并校验最终对象。

```ts
import { experimental_useObject, jsonSchema } from '@halo-dev/ai-foundation-sdk'

const object = experimental_useObject<{
  title: string
  summary: string
}>({
  api: '/apis/example.halo.run/v1alpha1/object/stream',
  schema: jsonSchema({
    type: 'object',
    properties: {
      title: { type: 'string' },
      summary: { type: 'string' },
    },
    required: ['title', 'summary'],
  }),
})

await object.submit('总结这篇文章')
```

请求 body 会同时包含 `schema` 和 `output`：

```json
{
  "input": "总结这篇文章",
  "schema": { "type": "object" },
  "output": {
    "type": "object",
    "schema": { "type": "object" }
  }
}
```

endpoint 应返回生成出的 JSON 对象文本流，通常是 `text/plain` 或其他可读取的普通文本响应。
`schema` 可以是 JSON Schema 对象，也可以是能通过 `toJSONSchema()` 或 `toJsonSchema()`
导出 JSON Schema 的同步 schema 对象。

## 持久化 Helpers

调用方需要保存或重新加载 UI messages 时，可以使用持久化 helper。

```ts
import {
  assertValidUIMessages,
  pruneMessages,
  validateUIMessages,
} from '@halo-dev/ai-foundation-sdk'

const pruned = pruneMessages(messages, { maxMessages: 20 })
const issues = validateUIMessages(pruned, { dataPartSchemas })

assertValidUIMessages(pruned)
```

`pruneMessages` 在设置 `maxMessages` 时保留最近的消息，默认移除 pending tool part，并丢弃
没有剩余 part 的消息。已完成、已拒绝、已响应审批和执行失败的 tool part 会被保留。

`validateUIMessages` 返回 `{ path, code, message }` issue 列表。
`assertValidUIMessages` 在存在 issue 时抛出 `AIUIMessageValidationError`。这些 helper 只负责
校验和清理 UI message 结构；转换为模型 message 由后端 Java `UIMessageConverters` 完成。

## 工具续跑

Halo UIMessage stream 会把工具调用表示为 assistant message 上的动态 `tool-*` part。外部
工具或审批步骤完成后，更新同一个 assistant message，再重新发送当前消息列表即可继续生成。

canonical tool chunks 包括：

- `tool-input-start`
- `tool-input-delta`
- `tool-input-available`
- `tool-output-available`
- `tool-output-error`
- `tool-approval-request`
- `tool-approval-response`

reducer 会把这些事件聚合为最终的 `tool-*` message part。`start-step` 和 `finish-step` 是
生命周期事件，不会写入 `message.parts`。

```ts
await chat.addToolOutput({
  toolCallId: 'call_1',
  output: { title: 'Halo' },
})

await chat.rejectToolCall({
  id: 'approval_call_1',
  reason: '用户拒绝执行',
})
```

`addToolOutput` 可以从已有的 `tool-*` part 推断 `toolName`。`addToolApprovalResponse` 可以从
已有审批请求推断 `toolCallId` 和 `toolName`。`rejectToolCall` 是
`addToolApprovalResponse({ approved: false })` 的快捷方式；拒绝审批不会被视为工具执行错误。

如果 `sendAutomaticallyWhen` 返回 `true`，`Chat` 会在工具续跑 part 追加后再次发送请求。
导出的 `lastAssistantMessageIsCompleteWithApprovalResponses` predicate 适合审批驱动的续跑。

```ts
import {
  lastAssistantMessageIsCompleteWithApprovalResponses,
  useChat,
} from '@halo-dev/ai-foundation-sdk'

const chat = useChat({
  transport,
  sendAutomaticallyWhen: lastAssistantMessageIsCompleteWithApprovalResponses,
})
```

## OpenAPI 生成客户端

不建议直接用生成的 Axios operation 方法消费 streaming endpoint。这类方法通常返回
`Promise`，不适合读取浏览器原生 `ReadableStream` 或 SSE。推荐用生成的 Axios 参数构造器
生成 URL、headers 和 body，然后交给本 SDK 的 fetch 与 stream 读取逻辑。

```ts
import { DefaultChatTransport, fromOpenAPIRequestArgs, useChat } from '@halo-dev/ai-foundation-sdk'
import { ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator } from './api/generated'

const paramCreator = ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator()

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '',
    prepareSendMessagesRequest: async ({ body }) => {
      const args = await paramCreator.testModelUiMessageChatStream(
        'model-name',
        body,
        undefined,
        undefined,
        undefined,
      )
      return fromOpenAPIRequestArgs(args, body)
    },
  }),
})
```

`useCompletion` 和 `experimental_useObject` 也通过 `prepareRequest` 支持同样模式：

```ts
const completion = useCompletion({
  prepareRequest: async ({ body }) => {
    const args = await paramCreator.testModelCompletionStream('model-name', body)
    return fromOpenAPIRequestArgs(args, body)
  },
})

const object = experimental_useObject({
  schema,
  prepareRequest: async ({ body }) => {
    const args = await paramCreator.testModelObjectStream('model-name', body)
    return fromOpenAPIRequestArgs(args, body)
  },
})
```

## 底层导出

需要构建自定义适配层时，可以直接使用这些导出：

- `Chat`、`createPlainChatState`、`lastAssistantMessageIsCompleteWithApprovalResponses`
- `DefaultChatTransport`、`HttpChatTransport`、`TextStreamChatTransport`、`createUserMessage`
- `fromOpenAPIRequestArgs`
- `readUIMessageStream`、`readUIMessageSSEStream`、`readTextStream`、`collectText`
- `createUIMessageReducer`、`applyUIMessageChunk`、`messageText`
- `parsePartialJson`、`toJsonSchema`、`validateFinalValue`、`validateRuntimeSchema`
- `pruneMessages`、`validateUIMessages`、`assertValidUIMessages`
- 错误类：`AIUIError`、`AIUIProtocolError`、`AIUISchemaValidationError`、
  `AIUIMessageValidationError`
- 协议常量：`DONE_MARKER`、`HALO_UI_MESSAGE_STREAM_HEADER`、
  `HALO_UI_MESSAGE_STREAM_VERSION`

## 当前限制

当前包不提供 resume、reconnect、replay、active stream registry、file 上传管理、
`source-document` 占位协议处理，或 npm 侧 UI message 到模型 message 的转换。

## 开发

从仓库根目录运行：

```sh
pnpm --dir ui --filter @halo-dev/ai-foundation-sdk typecheck
pnpm --dir ui --filter @halo-dev/ai-foundation-sdk test
pnpm --dir ui --filter @halo-dev/ai-foundation-sdk build
```
