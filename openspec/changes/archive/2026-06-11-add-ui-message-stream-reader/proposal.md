## Why

Callers that do not want to use `Chat` or `useChat` currently have to manually combine low-level SSE parsing, `UIMessage` reduction, schema validation, and lifecycle callbacks. A high-level stream reader gives custom clients the same runtime semantics without taking ownership of HTTP request construction or chat workflow state.

## What Changes

- Add a public `readUIMessageStream` helper to `@halo-dev/ai-foundation-sdk` for reading an existing Halo UIMessage stream into an assistant message result.
- Support `AsyncIterable<UIMessageChunk>`, UIMessage SSE `ReadableStream<Uint8Array>`, and `Response` inputs.
- Reuse the existing reducer, runtime schema hooks, data callback semantics, and one-time tool-call notification semantics.
- Return structured lifecycle results including final message, terminal information, status, error flags, and optional errors.
- Document the helper as a low-level custom stream consumer for callers that manage requests or state themselves.
- Fix the package-local test configuration so `pnpm --dir ui/packages/ai-ui-vue test` runs the package tests.

Non-goals:

- Do not add stream resume, reconnect, replay, or active stream registry behavior.
- Do not add text stream or object stream support to this helper.
- Do not make the helper responsible for `fetch` request creation, HTTP status handling, or transport preparation.
- Do not add automatic tool continuation or automatic tool output writing.
- Do not refactor `Chat.consumeAssistantStream` in this change.
- Do not change Java backend APIs or the model test workbench runtime.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-ui-vue-package`: add a public high-level UIMessage stream reader helper and package-local test command behavior.
- `consumer-sdk-documentation`: document custom UIMessage stream consumption and its boundary relative to `Chat` / `useChat`.

## Impact

- `ui/packages/ai-ui-vue/src/stream-reader.ts`: new high-level stream reader implementation.
- `ui/packages/ai-ui-vue/src/index.ts`: export the helper and related types.
- `ui/packages/ai-ui-vue/src/core.test.ts`: focused tests for stream inputs, lifecycle callbacks, schema behavior, error handling, and result status.
- `ui/packages/ai-ui-vue/rstest.config.ts`: make package-local test discovery work from the package directory.
- `ui/packages/ai-ui-vue/README.md` and `dev/ui-message-stream.md`: document usage and boundaries.
- OpenSpec specs for the npm runtime and consumer documentation.
