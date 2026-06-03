## Context

The language model API already supports request-scoped tools, schema validation, multi-step `stopWhen` loops, streaming tool events, lifecycle callbacks, and provider-neutral `ModelMessage` content parts. Today, once a model returns a valid executable tool call and the step control allows continuation, the app layer executes the server-side tool immediately.

Tool approval is modeled as a two-call workflow: the first call returns a tool approval request instead of blocking, the application records a decision in message history, and the next call executes or denies the tool before asking the model to continue. This shape fits Halo AI Foundation because callers already own conversation history through `ModelMessage`, and the plugin does not need durable pending approval state.

## Goals / Non-Goals

**Goals:**
- Add a provider-neutral Java SDK API for declaring static or dynamic tool approval requirements.
- Represent approval requests and responses as typed Halo content parts that can be persisted with `ModelMessage` history.
- Make `generateText` and `streamText` emit pending approval requests instead of executing tools that require approval.
- Resume approved or denied tool calls from message history on the next generation call.
- Preserve existing tool validation, stop condition, lifecycle callback, timeout, and stream projection behavior.

**Non-Goals:**
- No Console UI for approval review.
- No server-side pending approval store, queue, expiry, or lock.
- No role-specific approval permissions beyond the existing super administrator-oriented plugin scope.
- No provider-native approval protocol. Providers still see normal tool definitions and tool-result history.

## Decisions

### Approval lives in message history

Add `tool-approval-request` and `tool-approval-response` content parts to the public message model. The request part belongs to the assistant message that records the model's pending tool call. The response part belongs to a tool message supplied by the caller on a later request.

Alternative considered: keep approvals in a server-side pending table. That would complicate stateless SDK usage, require expiry and ownership semantics, and make streaming/non-streaming behavior diverge. Message history keeps approval portable across plugins and matches the existing tool result/error flow.

### Tool definitions own approval policy

Extend `ToolDefinition` with a provider-neutral approval policy. The normal API should support `always` and `never`, plus a dynamic predicate that receives `ToolExecutionContext` after input parsing and schema validation. Dynamic decisions can inspect the parsed input, tool call identity, step index, and current messages without exposing Spring AI types.

Alternative considered: request-level approval rules keyed by tool name. That is useful later for global policies, but placing the default policy beside the executor keeps tool behavior self-contained and mirrors the current request-scoped tool declaration model.

### Resumption happens before the next provider step

When a request contains prior approval request parts and matching approval response parts that have not yet produced tool results/errors, the generation run resolves them before invoking the provider. Approved responses execute the original tool and append tool results. Denied responses append a safe tool error or denial result that the provider can see as tool history. The provider is then called with the enriched messages.

Alternative considered: send approval responses directly to the provider without local execution. That would leave approved calls unexecuted and force the model to recreate the same tool call, making approval IDs and idempotency unreliable.

### Pending approval stops the current tool loop

If a step emits one or more tool calls and any executable call requires approval without a matching approval response, the run records approval request parts and finishes the current generation result without invoking those executors or starting another provider step. Calls that do not require approval may still execute only when the step has no pending approval requests; this avoids partially executing a mixed set where the model intended the tool calls as one coordinated action.

Alternative considered: execute non-approved tools while leaving approved tools pending. That is more permissive but can create confusing side effects before the user sees the complete requested action set.

### Stream parts mirror result parts

`streamText.fullStream()` emits existing `tool-call` parts, followed by `tool-approval-request` parts for pending calls. It does not emit `tool-result` or `tool-error` for pending calls until a later request provides approval responses. `textStream()` continues to emit only answer text.

Alternative considered: hide approval requests from streams and expose them only in final result projections. That would make streaming clients wait for completion before showing approval UI and would not match existing progressive tool event behavior.

## Risks / Trade-offs

- Approval response replay can execute a tool more than once if a caller resubmits the same history without also storing the resulting tool result. Mitigation: document that callers must persist returned response messages after approval execution, and make the runtime skip approval responses already followed by a matching result or error.
- Dynamic approval predicates may throw. Mitigation: treat predicate failure as a safe tool error and do not invoke the executor.
- Mixed approved and pending tool calls are conservative. Mitigation: document that pending approvals stop the step; callers can split risky tools into separate model turns when needed.
- Approval IDs must be stable enough to match across calls. Mitigation: generate IDs from the tool call id when available and include both approval id and tool call id in request/response parts.

## Migration Plan

This plugin is unreleased, so no compatibility layer is required. Implement the API and app behavior together, update tests and documentation, then validate with OpenSpec and Gradle checks. Rollback is reverting the branch before release.
