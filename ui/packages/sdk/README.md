# @halo-dev/ai-foundation-sdk

English | [中文](./README.zh-CN.md)

`@halo-dev/ai-foundation-sdk` is the browser and Vue UI SDK for Halo AI Foundation
streams. It helps frontends send chat, completion, and object-stream requests,
consume Halo UIMessage SSE responses, reduce stream chunks into UI messages, and
validate or persist those messages.

This package is not a backend provider SDK. Model selection, provider
configuration, and conversion from UI messages to model messages stay on the
Halo AI Foundation backend.

## Install

```sh
pnpm add @halo-dev/ai-foundation-sdk
```

`vue` 3.5 is a peer dependency. The package is safe to import in SSR and test
environments because browser APIs such as `fetch` are read only when a request is
started. Pass a custom `fetch` when the runtime does not provide one.

## Chat

Use `useChat` in Vue components. It manages `UIMessage` state and sends a
`UIMessageChatRequest` through a chat transport.

```ts
import { DefaultChatTransport, useChat } from '@halo-dev/ai-foundation-sdk'

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '/apis/example.halo.run/v1alpha1/chat/stream',
  }),
  experimental_throttle: { intervalMs: 50 },
})

await chat.sendMessage({ text: 'Hello' })
```

`DefaultChatTransport` posts JSON. The final body merges transport-level body,
per-call body, and the chat request fields:

```json
{
  "id": "conversation-1",
  "messages": [],
  "trigger": "submit-message",
  "messageId": null
}
```

The stream endpoint should return Server-Sent Events whose `data:` payloads are
JSON `UIMessageChunk` objects. A final `data: [DONE]` frame is accepted and
ignored. If the response includes `X-Halo-AI-UI-Message-Stream`, the value must
be `v1`.

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
```

`useChat` returns readonly Vue refs for `messages`, `status`, `error`, and
`isLoading`, plus these operations:

- `sendMessage({ text, files, parts, metadata })`
- `regenerate({ messageId })`
- `stop()`
- `setMessages(messages)`
- `clearError()`
- `addToolOutput(...)`
- `addToolApprovalResponse(...)`
- `rejectToolCall(...)`

If the endpoint returns plain text instead of Halo UIMessage SSE, use
`TextStreamChatTransport`. It wraps the text deltas into one assistant text part.

```ts
import { TextStreamChatTransport, useChat } from '@halo-dev/ai-foundation-sdk'

const chat = useChat({
  transport: new TextStreamChatTransport({ api: '/apis/example.halo.run/v1alpha1/chat/text' }),
})
```

`experimental_throttle` throttles only Vue-visible `messages` commits. Stream
consumption, reducer updates, `onData`, `onToolCall`, and terminal-state flushes
are not delayed. `undefined`, `0`, and negative values disable throttling.

## Runtime Schemas

`useChat`, `Chat`, and `readUIMessageStream` can validate streamed message
metadata and persistent `data-*` parts at runtime.

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

`messageMetadataSchema` validates the merged metadata each time metadata is
updated. `dataPartSchemas` is keyed by data name, so a `data-status` chunk uses
the `status` schema. Supported schema shapes are JSON Schema objects,
`safeParse` / `parse` objects, and synchronous Standard Schema adapters. Async
schemas are rejected for UI message streams.

When validation fails, the SDK throws `AIUISchemaValidationError`, sets chat
`error`, calls `onError`, and finishes the stream with `isError: true`.

## Reading UIMessage Streams

Use `readUIMessageStream` when you already manage request sending and message
state, or when you need a non-Vue stream consumer.

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

`readUIMessageStream` can read from an `AsyncIterable<UIMessageChunk>`, a
`ReadableStream<Uint8Array>`, or a `Response`. It does not send the request,
check `response.ok`, parse non-stream error responses, reconnect, replay, or
continue tool calls automatically. `onChunk` receives the raw protocol chunk
before reducer validation can fail; `onMessage`, `onData`, and `onToolCall` run
after the reducer accepts the chunk.

## Completion

`useCompletion` posts `{ prompt, ...body }` and reads a plain text stream.

```ts
import { useCompletion } from '@halo-dev/ai-foundation-sdk'

const completion = useCompletion({
  api: '/apis/example.halo.run/v1alpha1/completion/stream',
  body: { temperature: 0.2 },
})

await completion.complete('Write a title')
```

The returned object includes `completion`, `input`, `error`, `isLoading`,
`complete`, `stop`, `setCompletion`, `setInput`, `handleInputChange`, and
`handleSubmit`.

Per-call request options are merged with the configured `headers`, `body`, and
`credentials`.

```ts
await completion.complete('Write a title', {
  body: { requestId: 'request-1' },
  headers: { 'X-Request-Id': 'request-1' },
  credentials: 'include',
})
```

## Object Streaming

`experimental_useObject` posts `{ input, schema, output, ...body }` and reads a
JSON text stream. While streaming, it attempts to parse partial JSON snapshots.
When the stream ends, it parses and validates the final object.

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

await object.submit('Summarize this article')
```

The request body includes both `schema` and `output`:

```json
{
  "input": "Summarize this article",
  "schema": { "type": "object" },
  "output": {
    "type": "object",
    "schema": { "type": "object" }
  }
}
```

The endpoint should return the generated JSON object as a readable text stream,
usually `text/plain` or another plain text response. `schema` may be a JSON
Schema object or a sync schema object that can export JSON Schema through
`toJSONSchema()` or `toJsonSchema()`.

## Persistence Helpers

Use the persistence helpers when saving or reloading UI messages.

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

`pruneMessages` keeps the newest messages when `maxMessages` is set, removes
pending tool parts by default, and drops messages with no remaining parts.
Completed, denied, responded, and errored tool parts are retained.

`validateUIMessages` returns `{ path, code, message }` issues.
`assertValidUIMessages` throws `AIUIMessageValidationError` when issues exist.
These helpers validate and prune the UI message shape only; backend Java
`UIMessageConverters` handle conversion to model messages.

## Tool Continuation

Halo UIMessage streams represent tool calls as dynamic `tool-*` parts on the
assistant message. After an external tool or approval step finishes, update that
same assistant message and send the current message list again.

Canonical tool chunks include:

- `tool-input-start`
- `tool-input-delta`
- `tool-input-available`
- `tool-output-available`
- `tool-output-error`
- `tool-approval-request`
- `tool-approval-response`

The reducer aggregates those events into final `tool-*` message parts.
`start-step` and `finish-step` are lifecycle events and are not stored in
`message.parts`.

```ts
await chat.addToolOutput({
  toolCallId: 'call_1',
  output: { title: 'Halo' },
})

await chat.rejectToolCall({
  id: 'approval_call_1',
  reason: 'User rejected the action',
})
```

`addToolOutput` can infer `toolName` from the existing `tool-*` part.
`addToolApprovalResponse` can infer both `toolCallId` and `toolName` from an
existing approval request. `rejectToolCall` is a shortcut for
`addToolApprovalResponse({ approved: false })`; rejection is not treated as a
tool execution error.

If `sendAutomaticallyWhen` returns `true`, `Chat` sends another request after a
tool continuation part is appended. The exported
`lastAssistantMessageHasCompletedToolContinuations` predicate covers completed
tool outputs, tool errors, denied outputs, and approval responses without
continuing while another tool call is still pending.

```ts
import {
  lastAssistantMessageHasCompletedToolContinuations,
  useChat,
} from '@halo-dev/ai-foundation-sdk'

const chat = useChat({
  transport,
  sendAutomaticallyWhen: lastAssistantMessageHasCompletedToolContinuations,
})
```

## OpenAPI Generated Clients

Do not consume streaming endpoints through generated Axios operation methods
directly. Those methods usually return a `Promise`, which is not a good fit for
browser `ReadableStream` or SSE consumption. Instead, use the generated Axios
parameter creator to build URL, headers, and body, then let this SDK handle
fetch and stream reading.

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

`useCompletion` and `experimental_useObject` expose the same pattern through
`prepareRequest`.

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

## Lower-Level Exports

Use these exports when building custom adapters:

- `Chat`, `createPlainChatState`, `lastAssistantMessageHasCompletedToolContinuations`
- `DefaultChatTransport`, `HttpChatTransport`, `TextStreamChatTransport`, `createUserMessage`
- `fromOpenAPIRequestArgs`
- `readUIMessageStream`, `readUIMessageSSEStream`, `readTextStream`, `collectText`
- `createUIMessageReducer`, `applyUIMessageChunk`, `messageText`
- `parsePartialJson`, `toJsonSchema`, `validateFinalValue`, `validateRuntimeSchema`
- `pruneMessages`, `validateUIMessages`, `assertValidUIMessages`
- Error classes: `AIUIError`, `AIUIProtocolError`, `AIUISchemaValidationError`,
  `AIUIMessageValidationError`
- Protocol constants: `DONE_MARKER`, `HALO_UI_MESSAGE_STREAM_HEADER`,
  `HALO_UI_MESSAGE_STREAM_VERSION`

## Current Limits

The package does not implement resume, reconnect, replay, an active stream
registry, file upload management, `source-document` placeholder handling, or npm
side conversion from UI messages to model messages.

## Development

From the repository root:

```sh
pnpm --dir ui --filter @halo-dev/ai-foundation-sdk typecheck
pnpm --dir ui --filter @halo-dev/ai-foundation-sdk test
pnpm --dir ui --filter @halo-dev/ai-foundation-sdk build
```
