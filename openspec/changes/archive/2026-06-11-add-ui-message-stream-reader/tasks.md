## 1. Stream Reader Core

- [x] 1.1 Add `stream-reader.ts` with public option/result/status types.
- [x] 1.2 Implement input normalization for `AsyncIterable<UIMessageChunk>`, UIMessage SSE `ReadableStream<Uint8Array>`, and `Response`.
- [x] 1.3 Reuse `createUIMessageReducer` with `message`, `messageId`, `metadata`, `generateId`, `messageMetadataSchema`, and `dataPartSchemas`.
- [x] 1.4 Implement shallow snapshot helpers for messages and data/tool parts.

## 2. Reader Lifecycle Semantics

- [x] 2.1 Call `onChunk` before reducer validation and accepted runtime callbacks after reducer application.
- [x] 2.2 Trigger `onMessage` only for visible accepted message updates and skip standalone `error` / `abort` chunks.
- [x] 2.3 Trigger `onData` for persistent parsed data and transient raw callback-only data.
- [x] 2.4 Trigger `onToolCall` at most once per `toolCallId` and keep it notification-only.
- [x] 2.5 Implement status handling for `ready`, `error`, `disconnected`, and `aborted`.
- [x] 2.6 Implement `onError`, `onFinish`, `throwOnError`, callback failure handling, and final result behavior.
- [x] 2.7 Export `readUIMessageStream` and its public types from the package entrypoint.

## 3. Tests

- [x] 3.1 Fix package-local Rstest config so `pnpm --dir ui/packages/ai-ui-vue test` discovers tests.
- [x] 3.2 Add tests for async chunk stream, readable SSE stream, and response input.
- [x] 3.3 Add tests for schema hooks, parsed data callbacks, transient data callbacks, and one-time tool callbacks.
- [x] 3.4 Add tests for error, disconnected, aborted, `throwOnError`, `onFinish`, and callback failure behavior.
- [x] 3.5 Add tests that `error` chunks populate terminal state without throwing.

## 4. Documentation

- [x] 4.1 Update `ui/packages/ai-ui-vue/README.md` with custom `readUIMessageStream` usage and limitations.
- [x] 4.2 Update `dev/ui-message-stream.md` with the lower-level stream reader workflow and fetch boundary.
- [x] 4.3 Ensure documentation positions `Chat` / `useChat` as the standard chat path and the reader as the custom consumer path.

## 5. Verification

- [x] 5.1 Run `pnpm --dir ui/packages/ai-ui-vue test`.
- [x] 5.2 Run `pnpm --dir ui/packages/ai-ui-vue typecheck`.
- [x] 5.3 Run `pnpm --dir ui type-check` and `pnpm --dir ui lint`.
- [x] 5.4 Run `openspec validate add-ui-message-stream-reader --strict` and final repository checks.
