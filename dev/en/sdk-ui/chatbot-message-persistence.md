# SDK UI: Message persistence

[简体中文](../../zh-CN/sdk-ui/chatbot-message-persistence.md) | English

Persist complete `UIMessage` values, not rendered text. Part order carries reasoning, sources,
files, custom data, tool state, approvals, and step boundaries.

```ts
const loaded = await loadMessages(chatId);
assertValidUIMessages(loaded, {
    messageMetadataSchema,
    dataPartSchemas,
});

const chat = useChat({ id: chatId, messages: loaded, transport });
```

`validateUIMessages` returns `{ path, code, message }` issues. `assertValidUIMessages` throws
`AIUIMessageValidationError` when any issue exists.

Prune only with explicit limits:

```ts
const compact = pruneMessages(messages, {
    maxMessages: 30,
    removePendingToolParts: true,
});
```

The helper keeps recent messages, removes pending tool parts by default, and drops messages that
contain only step markers. It does not estimate tokens or summarize context.

On the Java backend, save `finish.messages()` for the updated conversation or
`finish.responseMessage()` for only this assistant response. Decide explicitly whether aborted or
error partial messages are durable.

Before another model call, validate and convert persisted messages:

```java
List<UIMessage<ChatMetadata>> valid = UIMessageValidators.validate(messages);
List<ModelMessage> modelMessages = UIMessageConverters.toModelMessages(valid);
```

Register data, metadata, and tool validators or custom data/part converters when the application
extends the base protocol. Keep `step-start` markers and `toolCallId` associations in their
original order.

Format validation does not prove ownership. Apply authorization to chat IDs, metadata, data,
files, and source URLs, and never persist API keys in UI messages.
