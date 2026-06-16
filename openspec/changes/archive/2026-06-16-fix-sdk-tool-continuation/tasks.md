## 1. SDK Runtime

- [x] 1.1 Add `maxAutomaticSteps` and `onAutomaticStepLimitExceeded` to `ChatInit` and `useChat` creation options.
- [x] 1.2 Replace single-step auto-submit gating with bounded multi-step continuation and completed-tool-state de-duplication.
- [x] 1.3 Commit assistant tool state before firing `onToolCall`, and surface synchronous or async callback failures through chat error state.
- [x] 1.4 Replace unreleased helper APIs with `lastAssistantMessageIsCompleteWithToolCalls` and `lastAssistantMessageHasRespondedToToolApprovals`.

## 2. Tests

- [x] 2.1 Cover multi-step automatic continuation across multiple client-side tool calls.
- [x] 2.2 Cover automatic step limit behavior and the limit callback.
- [x] 2.3 Cover committed-state `onToolCall` behavior and callback failure handling.
- [x] 2.4 Cover exported helper predicates and removed instance helper usage.
- [x] 2.5 Cover client-side tool output written before trailing `finish-step` / `finish` chunks.
- [x] 2.6 Cover consumed tool continuation idempotency when later responses append text or change assistant message ids.

## 3. Documentation And Validation

- [x] 3.1 Update `dev/ui-message-stream.md` with the recommended tool continuation pattern.
- [x] 3.2 Run OpenSpec validation for the change.
- [x] 3.3 Run focused SDK tests and TypeScript checks.
- [x] 3.4 Add console workbench cases for Agent tools and no-approval automatic tools.
