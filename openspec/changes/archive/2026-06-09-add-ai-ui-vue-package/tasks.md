## 1. Package Setup

- [x] 1.1 Extend `ui/pnpm-workspace.yaml` to include `ui/packages/*`.
- [x] 1.2 Create `ui/packages/ai-ui-vue` with package metadata for `@halo-dev/ai-foundation-sdk@0.1.0`.
- [x] 1.3 Configure package TypeScript, build, exports, declaration output, and test setup.
- [x] 1.4 Verify the selected build tool can produce ESM and `.d.ts` output without console-only dependencies.

## 2. Wire Types and Stream Utilities

- [x] 2.1 Add TypeScript types for Halo UI messages, parts, chunks, chat requests, statuses, finish metadata, and tool continuation values.
- [x] 2.2 Implement Halo UIMessage SSE parsing with `[DONE]` handling and optional `X-Halo-AI-UI-Message-Stream: v1` validation.
- [x] 2.3 Implement text stream reading utilities for chat text transport, completion, and object generation.
- [x] 2.4 Implement UIMessage chunk reduction into assistant message snapshots, including text, reasoning, data, source/file, tool, metadata, finish, error, and abort chunks.
- [x] 2.5 Add unit tests for parser success, parser errors, unsupported protocol versions, chunk reduction, and terminal states.

## 3. Chat Core and Transports

- [x] 3.1 Implement framework-neutral `Chat` with state adapter, generated ids, statuses, error handling, request sequencing, and abort behavior.
- [x] 3.2 Implement `DefaultChatTransport` for Halo UIMessage SSE using `UIMessageChatRequest`.
- [x] 3.3 Implement `TextStreamChatTransport` for plain text chat endpoints.
- [x] 3.4 Implement `sendMessage`, `regenerate`, `stop`, `setMessages`, `clearError`, and lifecycle callbacks.
- [x] 3.5 Implement tool continuation helpers for tool result, tool error, and approval response.
- [x] 3.6 Implement `sendAutomaticallyWhen` resubmission after stream finish or tool continuation.
- [x] 3.7 Add unit tests for chat state transitions, shared request body shape, regeneration truncation, cancellation, tool continuation, and automatic resubmission.

## 4. Vue Composables and Shared Stores

- [x] 4.1 Implement `useChat` as a Vue adapter around `Chat` with reactive state and scope cleanup.
- [x] 4.2 Implement id-keyed shared store behavior for `useChat`, `useCompletion`, and `experimental_useObject`.
- [x] 4.3 Implement `useCompletion` with `{ prompt, ...body }` requests, text stream consumption, input helpers, loading, stop, and error state.
- [x] 4.4 Implement `experimental_useObject` with `{ input, schema, output, ...body }` requests, partial JSON snapshots, stop, clear, loading, and final validation.
- [x] 4.5 Add optional JSON Schema/Zod handling helpers while keeping JSON Schema as the primary path.
- [x] 4.6 Add composable tests for Vue reactivity, shared ids, scope cleanup, completion streaming, object partial updates, object validation errors, and SSR import safety.

## 5. Backend Endpoint Support

- [x] 5.1 Review existing console endpoints and generated API docs for chat, completion, and object streaming coverage.
- [x] 5.2 Add or adjust backend request DTOs for completion and object streaming only where current endpoints cannot satisfy the package contracts.
- [x] 5.3 Implement object streaming request handling that prefers explicit `output` and derives object output from `schema` when needed.
- [x] 5.4 Ensure object streaming responses emit generated JSON text in order and rely on structured output validation for final acceptance.
- [x] 5.5 Add backend tests for completion request shape, object request shape, invalid output declarations, and streamed response behavior.
- [x] 5.6 Regenerate API docs/client only if backend endpoint contracts change.

## 6. Workbench Dogfood

- [x] 6.1 Add the local workspace dependency from the console UI to `@halo-dev/ai-foundation-sdk`.
- [x] 6.2 Migrate the model test workbench chat UIMessage stream state and parsing path to the package runtime.
- [x] 6.3 Preserve existing console layout, Chinese UI text, Markdown rendering, model attribution, parameter handling, and Halo components.
- [x] 6.4 Preserve stop behavior, reasoning display separation, external tool result/error continuation, and approval response continuation.
- [x] 6.5 Update or add workbench frontend tests for package-backed chat streaming and tool continuation.

## 7. Documentation

- [x] 7.1 Write the package README with install instructions, exports, `useChat`, `useCompletion`, `experimental_useObject`, transports, and backend protocol examples.
- [x] 7.2 Update `dev/ui-message-stream.md` with `@halo-dev/ai-foundation-sdk` chat endpoint integration guidance.
- [x] 7.3 Document object streaming request shape and final validation responsibilities in `dev/ui-message-stream.md`.
- [x] 7.4 Ensure docs use Halo-owned wording and avoid presenting the package as an AI SDK-branded wrapper.

## 8. Validation

- [x] 8.1 Run the package test suite.
- [x] 8.2 Run frontend type-check and relevant workbench tests.
- [x] 8.3 Run backend tests for changed endpoint behavior when backend code changes.
- [x] 8.4 Run `git diff --check` (raw check reports regenerated client whitespace; non-generated diff check passes).
- [x] 8.5 Review package dependency tree to confirm no console-only dependencies are bundled into `@halo-dev/ai-foundation-sdk`.
