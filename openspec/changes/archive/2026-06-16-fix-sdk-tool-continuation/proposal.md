## Why

Client-side Agent tool flows can stop after the first browser tool call because the TypeScript chat runtime only auto-submits one continuation request and does not provide a robust, documented helper for repeated tool-result continuation. This breaks multi-step agents that must inspect page context, return the result to the model, and then decide whether to continue with another tool or final text.

## What Changes

- Update the TypeScript `Chat` runtime to support bounded multi-step automatic continuation when callers explicitly configure `sendAutomaticallyWhen`.
- Add a default automatic continuation safety limit and an overflow callback so tool loops stop predictably without becoming chat errors.
- Ensure `onToolCall` observes a committed assistant message state before callers add tool output.
- Align the public helper surface with AI SDK UI naming by exporting `lastAssistantMessageIsCompleteWithToolCalls`.
- **BREAKING**: Remove the unreleased instance method and old approval-specific helper in favor of exported pure helper functions.
- Update SDK documentation for `onToolCall`, `addToolOutput`, `sendAutomaticallyWhen`, continuation limits, and approval response handling.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-ui-vue-package`: Refine TypeScript chat runtime tool continuation behavior and public helper APIs.

## Impact

- Affected code: `ui/packages/sdk/src/chat.ts`, `ui/packages/sdk/src/use-chat.ts`, `ui/packages/sdk/src/index.ts`, SDK tests, and `dev/ui-message-stream.md`.
- API impact: unreleased TypeScript SDK surface changes for tool continuation helpers and `ChatInit` options.
- Backend impact: none. The Java UI message stream protocol and Live2D backend request handling remain unchanged.
- Dependency impact: none.
