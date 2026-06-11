# @halo-dev/ai-ui-vue

面向 Vue 的 Halo AI UI 前端工具包，提供聊天、补全、结构化对象生成所需的 composable、传输层和流处理工具。

## 安装

```sh
pnpm add @halo-dev/ai-ui-vue
```

这个包可以安全地在 SSR 环境中导入。`fetch` 等浏览器 API 只会在真正发起请求时读取；服务端渲染、单元测试或自定义运行时中可以传入自己的 `fetch`。

## Chat

`useChat` 管理 Halo `UIMessage` 状态，并把 `UIMessageChatRequest` 发送到 Halo UIMessage SSE 端点。

```ts
import { DefaultChatTransport, useChat } from '@halo-dev/ai-ui-vue'

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '/apis/example.halo.run/v1alpha1/chat/stream',
  }),
})

await chat.sendMessage({ text: '你好' })
```

默认传输层会发送如下请求：

```json
{
  "id": "conversation-1",
  "messages": [],
  "trigger": "submit-message",
  "messageId": null
}
```

后端应返回 Halo UIMessage SSE：

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
```

每个 SSE `data:` 都是一个 JSON `UIMessageChunk`，正常结束时发送：

```text
data: [DONE]
```

`useChat` 返回只读的 `messages`、`status`、`error`、`isLoading`，以及这些操作方法：

- `sendMessage({ text })`
- `regenerate({ messageId })`
- `stop()`
- `setMessages(messages)`
- `clearError()`
- `addToolOutput(...)`
- `addToolApprovalResponse(...)`
- `rejectToolCall(...)`
- `isLastAssistantMessageToolComplete()`

如果后端只返回普通文本流，可以使用 `TextStreamChatTransport`。它会把文本增量包装成单个 assistant text part。

### Runtime Schemas

`useChat` 可以在接收 Halo UIMessage SSE 时校验 streamed metadata 和持久化 `data-*` part。
schema 支持 JSON Schema、`safeParse` / `parse` 风格对象，以及同步 Standard Schema。

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

`messageMetadataSchema` 校验合并后的 metadata。`dataPartSchemas` 使用 data name 作为 key；
例如 `data-status` 对应 `status`。校验通过后，解析后的值会写入 `messages`，持久化 data 的
`onData` 也会收到解析后的值。schema 失败会抛出 `AIUISchemaValidationError`，并进入
`error`、`onError` 和 `onFinish({ isError: true })` 路径。

## Completion

`useCompletion` 会发送 `{ prompt, ...body }`，并读取普通文本流。

```ts
import { useCompletion } from '@halo-dev/ai-ui-vue'

const completion = useCompletion({
  api: '/apis/example.halo.run/v1alpha1/completion/stream',
  body: { temperature: 0.2 },
})

await completion.complete('写一个标题')
```

返回值包含 `completion`、`input`、`error`、`isLoading`、`complete`、`stop`、`setCompletion`、`setInput`、`handleInputChange` 和 `handleSubmit`。

## Object Streaming

`experimental_useObject` 会发送 `{ input, schema, output, ...body }`，并读取模型生成的 JSON 文本流。读取过程中会尽量解析部分 JSON 快照，流结束后会用传入的 JSON Schema 或 Zod-like schema 校验最终对象。

```ts
import { experimental_useObject, jsonSchema } from '@halo-dev/ai-ui-vue'

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

请求中会包含：

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

后端应返回生成出的 JSON 对象文本流，通常是 `text/plain` 或其他可读取的普通文本响应。

## OpenAPI 生成客户端

流式响应不建议直接通过 Axios operation 方法消费，因为它通常返回 `Promise`，不适合读取浏览器原生 `ReadableStream` 或 SSE。推荐让 OpenAPI 生成客户端只负责构造 URL、headers 和请求体，再交给本包的 fetch 流处理逻辑消费。

```ts
import {
  DefaultChatTransport,
  fromOpenAPIRequestArgs,
  useChat,
} from '@halo-dev/ai-ui-vue'
import { ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator } from './api/generated'

const paramCreator = ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator()

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '',
    prepareSendMessagesRequest: async ({ body }) => {
      const args = await paramCreator.testModelUiMessageChatStream('model-name', body)
      return fromOpenAPIRequestArgs(args, body)
    },
  }),
})
```

`useCompletion` 和 `experimental_useObject` 也提供 `prepareRequest`：

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

- `Chat` 和 `createPlainChatState`
- `DefaultChatTransport`、`HttpChatTransport`、`TextStreamChatTransport`
- `fromOpenAPIRequestArgs`
- `readUIMessageSSEStream`、`readTextStream`、`collectText`
- `createUIMessageReducer`、`applyUIMessageChunk`、`messageText`
- `parsePartialJson`、`toJsonSchema`、`validateFinalValue`

## 工具续跑

Halo `UIMessage` 会把工具调用生命周期保存在 assistant message 的动态 `tool-*` part 中。外部工具或审批步骤完成后，更新原 assistant 消息中的同一个 tool part，再重新发送当前消息列表即可继续生成。

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

`addToolOutput` 会根据已有 `tool-*` part 自动补齐工具名称。`addToolApprovalResponse` 会根据已有审批请求补齐 `toolCallId` 和工具名称，并把审批请求标记为 `approval-responded`。`rejectToolCall` 是 `addToolApprovalResponse({ approved: false })` 的便捷别名。拒绝审批不是工具执行异常，不会被标记为 `output-error`。

如果配置了 `sendAutomaticallyWhen` 且返回 `true`，`Chat` 会在工具续跑 part 追加后自动再次提交。
