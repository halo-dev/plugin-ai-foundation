## Context

The TypeScript SDK already models Halo UI messages, dynamic `tool-*` parts, `onToolCall`, `addToolOutput`, approval responses, and explicit `sendAutomaticallyWhen` continuation. Live2D's Agent workflow exposed a gap: after a client-side page context tool returns, the SDK can perform one automatic continuation but then suppresses further automatic submissions in the same chain. Multi-step browser agents need repeated cycles of model request, client tool execution, tool output submission, and model continuation.

The SDK is unreleased, so this change can simplify the public surface instead of preserving compatibility shims.

## Goals / Non-Goals

**Goals:**

- Support bounded multi-step automatic continuation for client-side tool execution.
- Keep continuation opt-in through `sendAutomaticallyWhen`.
- Align the tool completion helper name with AI SDK UI: `lastAssistantMessageIsCompleteWithToolCalls`.
- Make `onToolCall` safer by committing the assistant tool part before notifying callers.
- Replace unreleased helper/method names with a smaller pure-function API.
- Document the recommended tool continuation pattern.

**Non-Goals:**

- Do not change the Java UI message wire protocol or backend stream conversion.
- Do not make `onToolCall` a managed tool executor that automatically writes tool outputs.
- Do not enable automatic continuation by default.
- Do not add new dependencies.

## Decisions

1. Automatic continuation remains explicit.

   Callers must configure `sendAutomaticallyWhen`; the SDK will not default to continuing whenever tool results appear. This keeps chat usage predictable and matches AI SDK UI's explicit helper pattern.

2. Multi-step continuation is bounded by `maxAutomaticSteps`.

   `ChatInit.maxAutomaticSteps` defaults to `5` and applies to one automatic continuation chain. When the limit is reached, the chat remains `ready`, no error is emitted, and `onAutomaticStepLimitExceeded` receives the current messages and limit. This prevents runaway client/server request loops while preserving recoverable state.

3. Continuation de-duplication uses the last assistant tool-result snapshot.

   The SDK records a stable signature built from the last assistant message id and completed tool result parts. Repeated checks for the same state do not resubmit; new tool outputs or changed tool result payloads can trigger another automatic step. The signature uses internal stable JSON serialization with sorted object keys and circular-reference protection.

4. Tool callbacks observe committed message state.

   The reducer will update chat messages before invoking `onToolCall`. This lets synchronous or immediately-resolving tool handlers call `addToolOutput` without racing the message write.

5. `onToolCall` stays notification-oriented.

   The callback type may return `void | Promise<void>`, but the SDK does not await it and does not convert return values into tool outputs. Synchronous throws and rejected promises are surfaced as chat errors to make broken handlers visible.

6. Public helper API becomes pure-function based.

   The SDK exports `lastAssistantMessageIsCompleteWithToolCalls` for normal tool results and `lastAssistantMessageHasRespondedToToolApprovals` for approval responses. The unreleased `Chat.isLastAssistantMessageToolComplete()` instance method and older approval-specific helper are removed.

## Risks / Trade-offs

- [Risk] A caller sets a too-broad `sendAutomaticallyWhen` and consumes all automatic steps without progress. -> Mitigation: default step limit, state-signature de-duplication, and an overflow callback.
- [Risk] Stable signatures for large tool outputs add overhead. -> Mitigation: signatures only include the last assistant's completed tool result parts and use a small dependency-free serializer.
- [Risk] Async `onToolCall` rejection timing differs from stream errors. -> Mitigation: normalize callback failures through the existing chat error state and `onError`.
- [Risk] Removing unreleased APIs requires local callers to update. -> Mitigation: update SDK tests and documentation in the same change; no compatibility layer is needed before release.
