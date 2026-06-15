## 1. Protocol And Fixture Baseline

- [x] 1.1 Define the final dynamic `data-*` and `tool-*` wire shapes in Java and TypeScript notes before editing implementation files.
- [x] 1.2 Add the minimal shared fixture directory for data parts, tool lifecycle, and terminal states.
- [x] 1.3 Add Java and TypeScript fixture readers so both sides can consume the same protocol examples.

## 2. Java Protocol Model

- [x] 2.1 Replace the split data/tool/approval part model with dynamic data and tool part records using strongly typed protocol fields.
- [x] 2.2 Add `UIMessagePartIdentity` for text, reasoning, source, file, dynamic data, and dynamic tool identities.
- [x] 2.3 Add dynamic name validation for `data-*` and `tool-*` type/name consistency.
- [x] 2.4 Update transport map codec serialization and deserialization for the new dynamic parts and chunks.
- [x] 2.5 Remove unreleased split tool result, tool error, and tool approval response APIs from the public UI message model.

## 3. Java Validation And Reduction

- [x] 3.1 Implement `UIMessageChunkValidator` for dynamic data and tool chunk structure.
- [x] 3.2 Refactor chunk reduction into `UIMessageChunkReducer` with persisted data updates and transient data skipping.
- [x] 3.3 Implement dynamic tool lifecycle reduction from input streaming through terminal output states.
- [x] 3.4 Update `UIMessageStreamReader` to delegate identity, validation, and reduction to focused classes.
- [x] 3.5 Update Java tests to verify shared fixtures reduce to the expected messages.

## 4. Java Conversion And Chat Handler

- [x] 4.1 Update `UIMessageValidators` for dynamic data/tool base validation and optional payload validators.
- [x] 4.2 Update `UIMessageConverters` to convert dynamic terminal tool states and skip pending tool states.
- [x] 4.3 Update `UIMessageChatHandlers` so validation, conversion, model streaming, response creation, and finish aggregation use the new reducer model.
- [x] 4.4 Verify handler output is readable by the Java reader and by the TypeScript reducer fixture path.

## 5. TypeScript Runtime Core

- [x] 6.1 Update package types for dynamic data/tool parts, lifecycle states, generics, and `disconnected` status.
- [x] 6.2 Refactor the TypeScript message reducer for dynamic data identity, transient callbacks, and dynamic tool lifecycle updates.
- [x] 6.3 Add runtime protocol validation for dynamic type/name consistency and required lifecycle fields.
- [x] 6.4 Update `Chat` core with `onData`, `onToolCall`, `addToolOutput`, `rejectToolCall`, and `isLastAssistantMessageToolComplete`.
- [x] 6.5 Add stream interruption handling that distinguishes post-start `disconnected` interruptions from request/protocol `error`.
- [x] 6.6 Update TypeScript tests to validate shared fixtures, data callbacks, tool callbacks, automatic continuation, stop, and error.

## 6. Vue Adapter And Helper APIs

- [x] 7.1 Update `useChat` to expose the new core state and actions without managing input state.
- [x] 7.2 Add `useChat({ chat })` support and fail fast when an existing chat is mixed with creation options.
- [x] 7.3 Add `sendMessage` file convenience input without adding browser upload management.
- [x] 7.4 Remove old public `addToolResult`, `addToolError`, and `addToolApprovalResponse` exports.
- [x] 7.5 Update `useCompletion` for per-call request options.
- [x] 7.6 Update `experimental_useObject` to use `initialValue`, generic submit input, and per-call request options.

## 7. Workbench Dogfood

- [x] 8.1 Update the backend UI message test path to emit the stabilized dynamic data/tool protocol.
- [x] 8.2 Update the workbench UI message mode so send, stop, regenerate, tool output, and tool rejection call the public `useChat` runtime.
- [x] 8.3 Keep a projection layer for existing workbench display fields while ensuring runtime `UIMessage` state is the source of truth.
- [x] 8.4 Verify workbench UI message mode covers text streaming, data events, tool lifecycle, stop, error, and regeneration.

## 8. Documentation

- [x] 9.1 Update `dev/ui-message-stream.md` with Java handler, dynamic data, dynamic tools, deferred recovery boundary, and Vue runtime examples.
- [x] 9.2 Keep `dev/dev.md` from duplicating the detailed UI message runtime guide.
- [x] 9.3 Update package README and public JavaDoc for changed caller-facing APIs.

## 9. Validation

- [x] 10.1 Run OpenSpec validation for `stabilize-ui-message-runtime`.
- [x] 10.2 Run focused Java tests for UI message model, reducer, validation, conversion, and chat handling.
- [x] 10.3 Run frontend package typecheck and tests for `@halo-dev/ai-foundation-sdk`.
- [x] 10.4 Run workbench-related frontend typecheck after dogfood wiring.
- [x] 10.5 Run a final search for removed old tool API references and update any remaining caller paths.
