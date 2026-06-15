## 1. Java UI Message Protocol

- [x] 1.1 Add typed Java UI message chunk records/factories for `start-step`, `tool-input-start`, `tool-input-delta`, `tool-input-available`, `tool-output-available`, `tool-output-error`, tool approval request, and tool approval response.
- [x] 1.2 Update Java chunk type constants, sealed permits, validators, and transport codec map conversion for the canonical chunk types.
- [x] 1.3 Update `UIMessageStreamMapper` so full-stream tool and step parts emit canonical UI chunks instead of dynamic `tool-<name>` chunks, while keeping `source-url` and `file` behavior unchanged.
- [x] 1.4 Update Java UI message stream reader/reducer aggregation so canonical tool chunks reduce into final dynamic tool parts and `start-step` remains lifecycle-only.
- [x] 1.5 Add or update focused Java tests that verify mapper output, codec round-trips, canonical tool aggregation, approval denial semantics, and `start-step` non-persistence.

## 2. Vue Package Protocol Runtime

- [x] 2.1 Add TypeScript chunk types for canonical tool chunks and `start-step`, preserving final dynamic `tool-<name>` message parts.
- [x] 2.2 Update package protocol validation to validate canonical tool chunk required fields and continue accepting legacy dynamic `tool-<name>` chunks as internal-compatible input.
- [x] 2.3 Update `applyUIMessageChunk`, `Chat`, and `readUIMessageStream` so canonical tool chunks reduce into final tool parts, `onToolCall` fires once on input availability, and `start-step` does not create visible message content.
- [x] 2.4 Update package tests for canonical tool input streaming, output/error chunks, approval request/response chunks, legacy dynamic chunk compatibility, and lifecycle-only `start-step`.

## 3. Persistence Helpers

- [x] 3.1 Implement and export `pruneMessages` with message-count pruning, default pending-tool-part removal, completed-tool retention, non-tool retention, and empty-message removal.
- [x] 3.2 Implement and export `validateUIMessages` with stable issue objects containing path, code, and message.
- [x] 3.3 Implement and export `assertValidUIMessages` and its public validation error type.
- [x] 3.4 Reuse existing synchronous schema contracts for persisted message metadata and dynamic data part validation where options are supplied.
- [x] 3.5 Add package tests for pruning defaults, validation issue paths/codes, throwing validation, schema success, and schema failure.

## 4. Vue Chat Throttling

- [x] 4.1 Add `experimental_throttle` option parsing for number and `{ intervalMs }` forms, treating omitted, zero, and negative values as disabled.
- [x] 4.2 Implement throttled Vue-visible message commits without throttling stream consumption, reducer application, `onData`, `onToolCall`, or error handling.
- [x] 4.3 Force flush pending message commits on finish, error, abort, disconnected state, stop, reset, and explicit `setMessages`.
- [x] 4.4 Add package tests that prove callbacks remain immediate while reactive message commits are throttled and terminal transitions flush immediately.

## 5. Documentation And Verification

- [x] 5.1 Update `dev/ui-message-stream.md` and package documentation to describe canonical tool stream chunks, final dynamic tool parts, `start-step`, persistence helpers, and throttle behavior.
- [x] 5.2 Ensure docs explicitly defer stream resume, `source-document`, file expansion, and npm-side model-message conversion.
- [x] 5.3 Run focused backend tests for UI message stream mapping/codec/reducer behavior.
- [x] 5.4 Run package tests for `@halo-dev/ai-foundation-sdk`.
- [x] 5.5 Run `git diff --check`.
