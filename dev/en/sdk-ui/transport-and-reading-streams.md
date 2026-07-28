# SDK UI: Transport and direct stream reading

[简体中文](../../zh-CN/sdk-ui/transport-and-reading-streams.md) | English

## HTTP transports

```ts
const transport = new DefaultChatTransport({
    api: "/apis/example.halo.run/v1alpha1/chat",
    credentials: "include",
    headers: () => ({ Authorization: `Bearer ${token.value}` }),
    body: () => ({ workspace: workspace.value }),
});
```

`headers`, `body`, and `credentials` may be values or sync/async resolvers.
`prepareSendMessagesRequest` can change the final URL, body, headers, or credentials.
`TextStreamChatTransport` wraps a plain text response in one assistant text part.

A custom `ChatTransport` implements:

```ts
interface ChatTransport<METADATA = unknown> {
    sendMessages(options: SendMessagesOptions<METADATA>): Promise<AsyncIterable<UIMessageChunk>>;
}
```

The transport owns I/O. `Chat` owns reduction, state, callbacks, errors, and automatic
continuation.

## Direct stream reader

```ts
const response = await fetch("/api/chat", request);
if (!response.ok) throw new Error(await response.text());

const result = await readUIMessageStream({
    response,
    messageId: "assistant-1",
    onChunk(chunk) {
        console.debug(chunk);
    },
    onMessage(message) {
        renderSnapshot(message);
    },
    onData: handleData,
    onToolCall: queueClientTool,
});
```

Input is one of `response`, `readableStream`, or an async iterable `stream`. Pass an existing
assistant `message` to continue reducing into it. Other options cover IDs, metadata, runtime
schemas, abort signals, callbacks, and `throwOnError`.

For each accepted chunk, callbacks run in this order: `onChunk`, reducer validation/application,
`onData`, first `onToolCall`, then visible `onMessage`. `onFinish` receives the final result.

Reader status is `ready`, `aborted`, `error`, or `disconnected`. Protocol-level `error` and
`abort` chunks update `result.terminal`; they are distinct from a reader exception. Check both
`status` and `terminal`.

The direct reader does not send requests, inspect `response.ok`, parse non-stream error bodies,
reconnect, or continue tools. Use `Chat` for the complete workflow.
