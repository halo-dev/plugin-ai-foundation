# UI Message Stream: Single-page reference

[简体中文](../zh-CN/ui-message-stream.md) | English

Halo UI Message is the persisted browser-facing conversation model. The Java bridge validates and
converts submitted messages, executes `LanguageModel.streamText`, maps model events to UI chunks,
and returns an SSE response. The browser SDK reduces those chunks into one assistant message.

## Minimal flow

```text
UIMessageChatRequest
  -> UIMessageChatHandlers
  -> validate and convert messages
  -> LanguageModel.streamText
  -> UIMessageStream
  -> UIMessageStreamResponse (SSE)
  -> DefaultChatTransport / readUIMessageStream
  -> persisted UIMessage
```

Use [Chatbot](./sdk-ui/chatbot.md) for the endpoint and Vue client,
[message persistence](./sdk-ui/chatbot-message-persistence.md) for durable state,
[tool interaction](./sdk-ui/chatbot-tool-usage.md) for browser tools and approvals, and the
[stream protocol](./sdk-ui/stream-protocol.md) for every wire chunk.

## Persistence rules

- Persist complete message parts in order.
- Keep `step-start` boundaries and stable IDs.
- Persistent data becomes `DataPart`; transient data is callback-only.
- Message metadata is merged separately from parts.
- Canonical tool chunks reduce into one dynamic tool part per `toolCallId`.
- Finish, error, and abort update terminal state and are not persisted as parts.
- Save provider reasoning metadata unchanged; Java conversion decides whether a model can reuse it.

## Extension points

- Java validation: metadata, named data, named or general tools.
- Java conversion: named data and custom part converters.
- Java stream creation: custom data, metadata, files, merged model streams, and finish callbacks.
- Browser runtime schemas: message metadata and named data.
- Browser transport: default SSE, plain text, OpenAPI request preparation, or custom transport.
- Browser controller: `Chat`, `useChat`, completion, object streams, and direct stream reading.

For every package export, see the [SDK UI public export index](./sdk-ui/api-reference.md). For the
complete Java UI bridge, see the [SDK Core public API index](./sdk-core/api-reference.md).
