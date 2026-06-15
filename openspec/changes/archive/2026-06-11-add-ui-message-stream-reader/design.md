## Context

The Vue runtime package already exposes low-level stream parsing through `readUIMessageSSEStream`, message aggregation through `createUIMessageReducer` / `applyUIMessageChunk`, and full chat workflow management through `Chat` / `useChat`. Callers with custom state management currently have to wire these pieces together themselves, which makes it easy to diverge from the package's schema validation, data callback, tool callback, and error lifecycle semantics.

This change is UI/npm-runtime only. It does not change Java backend APIs, stream protocol, model test workbench runtime, or transport request construction.

## Goals / Non-Goals

**Goals:**

- Provide `readUIMessageStream` as a high-level reader for existing UIMessage streams.
- Accept three stream inputs: `AsyncIterable<UIMessageChunk>`, UIMessage SSE `ReadableStream<Uint8Array>`, and `Response`.
- Reuse the existing reducer and runtime schema hooks.
- Provide lifecycle callbacks for raw chunks, visible message snapshots, data parts, tool calls, errors, and finish.
- Return a structured final result with message snapshot, terminal state, status, error flags, and optional error.
- Fix the package-local Rstest config so package tests run from `ui/packages/ai-ui-vue`.

**Non-Goals:**

- Do not send HTTP requests or own `fetch` request construction.
- Do not validate `response.ok` or parse non-stream HTTP error bodies.
- Do not support text streams or object JSON streams.
- Do not implement resume, reconnect, replay, stream ids, or active stream registries.
- Do not perform automatic tool continuation or convert `onToolCall` return values into tool output.
- Do not refactor `Chat.consumeAssistantStream` in this change.

## Decisions

### Reader Input Model

`readUIMessageStream` will accept one of:

- `stream: AsyncIterable<UIMessageChunk>`
- `readableStream: ReadableStream<Uint8Array>`
- `response: Response`

`stream` is the core path. `readableStream` is converted with `readUIMessageSSEStream`. `response` is a thin convenience wrapper that validates the Halo UIMessage stream header when present, requires `response.body`, and then uses `readUIMessageSSEStream(response.body)`. It does not check HTTP status.

### Reducer and Schema Reuse

The helper will construct `createUIMessageReducer` with `message`, `messageId`, `metadata`, `messageMetadataSchema`, and `dataPartSchemas`. If `message` is provided, it takes precedence over `messageId` and `metadata`. Initial metadata is not schema-validated; only streamed metadata is validated.

### Callback Ordering

For each chunk:

1. `onChunk` receives the raw chunk before reducer validation.
2. `applyUIMessageChunk` validates protocol/schema and mutates the reducer.
3. `onData` receives accepted data events. Persistent data receives parsed payloads; transient data receives raw callback-only payloads.
4. `onToolCall` fires once per `toolCallId` when a tool part reaches `input-available`.
5. `onMessage` receives a shallow-cloned visible assistant message snapshot only when the reducer has visible content and the chunk is not `error` or `abort`.

`onMessage`, `onData`, and `onToolCall` snapshots are shallow clones. Business payloads such as `data`, `input`, `output`, and `providerMetadata` are not deep-cloned.

### Lifecycle Result

The helper returns a result with:

- `message`
- `terminal`
- `status: 'ready' | 'error' | 'disconnected' | 'aborted'`
- `isError`
- `isAbort`
- `error?`

An empty stream returns an empty assistant message with `status: 'ready'`. A stream with only metadata returns the final reducer message but does not call `onMessage`.

### Error Behavior

The helper defaults to non-throwing lifecycle semantics. Schema/protocol/parser/callback errors set `status: 'error'`, call `onError`, call `onFinish`, and return the structured result. If `throwOnError` is true, it still calls `onError` and `onFinish`, then throws.

If a normal non-protocol error occurs after at least one accepted chunk has started the stream, the helper returns `status: 'disconnected'`. Protocol and schema errors remain `status: 'error'` even after streaming starts.

The helper accepts an external `abortSignal` but does not create or own an `AbortController`. If the signal is aborted, reading ends with `status: 'aborted'`. On schema failure it stops consuming and returns an error, but does not attempt to abort an external request.

Stream `error` chunks are reducer terminal data; they set `terminal.errorText` and do not automatically throw.

## Risks / Trade-offs

- [Risk] Some lifecycle code duplicates `Chat.consumeAssistantStream`. → Keep the first implementation scoped and tested; consider internal convergence only after both APIs stabilize.
- [Risk] Callback errors can be surprising if callers expect best-effort logging. → Document fail-fast behavior and test that callback errors enter the normal lifecycle.
- [Risk] `response` input may be mistaken for transport behavior. → Document that callers must handle `fetch`, HTTP status, headers, and request body themselves.
- [Risk] Shallow clones do not protect nested business payloads. → This keeps performance reasonable and matches runtime payload ownership expectations.
