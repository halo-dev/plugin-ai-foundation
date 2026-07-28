# SDK UI

[简体中文](../../zh-CN/sdk-ui/README.md) | English

SDK UI is the `@halo-dev/ai-foundation-sdk` browser and Vue package. A consumer backend resolves
models and produces a Halo UI Message stream; the frontend package sends requests, reduces stream
chunks, and manages UI state.

```bash
pnpm add @halo-dev/ai-foundation-sdk
```

Vue is a peer dependency. Importing the package is safe in SSR and tests; browser APIs such as
`fetch` are read only when a request starts, and a custom implementation can be injected.

## Guides

1. [Chatbot](./chatbot.md)
2. [Message persistence](./chatbot-message-persistence.md)
3. [Tool interaction](./chatbot-tool-usage.md)
4. [Completion and object streams](./completion-and-object-generation.md)
5. [Custom data and metadata](./streaming-data-and-metadata.md)
6. [Transport and direct stream reading](./transport-and-reading-streams.md)
7. [Stream protocol](./stream-protocol.md)
8. [Complete public export index](./api-reference.md)

| Need                    | Entry point                                   |
| ----------------------- | --------------------------------------------- |
| Vue chat state          | `useChat`                                     |
| Framework-neutral chat  | `Chat`                                        |
| Halo UI Message SSE     | `DefaultChatTransport`                        |
| Plain text chat stream  | `TextStreamChatTransport`                     |
| Custom transport        | `ChatTransport`                               |
| Direct aggregation      | `readUIMessageStream`                         |
| Completion              | `useCompletion`                               |
| Incremental JSON object | `experimental_useObject`                      |
| Persistence validation  | `validateUIMessages`, `assertValidUIMessages` |
| History pruning         | `pruneMessages`                               |

`UIMessage` is designed for rendering and persistence. Java
`UIMessageChatHandlers` / `UIMessageConverters` turn it into provider-neutral model messages.

Return to the [developer documentation](../README.md).
