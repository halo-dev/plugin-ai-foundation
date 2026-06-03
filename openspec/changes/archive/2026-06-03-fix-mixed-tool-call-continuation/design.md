## Context

The current tool loop supports server-side execution, external tool calls, approval requests, and repaired tool calls. The functional gap appears when a single model step returns multiple tool calls with mixed resolution states.

Provider tool histories require a complete pairing: every assistant tool call forwarded into a follow-up provider request must have a matching tool result or tool error. Today a mixed batch can record more tool calls than the system executes or resolves before starting continuation, creating incomplete history.

## Goals / Non-Goals

**Goals:**

- Keep provider continuation valid by continuing only when the current step's recorded tool calls are fully resolved.
- Stop non-streaming and streaming loops when any tool call is pending external execution.
- Stop non-streaming and streaming loops when any tool call is pending approval.
- Avoid recording executable tool calls as unresolved history when approval pauses a mixed batch.
- Preserve existing single-tool approval, external execution, repair, and response message behavior.

**Non-Goals:**

- Add a new public API for batching or partial tool execution.
- Execute approval-required tools before approval.
- Execute no-executor external tools server-side.
- Change provider-specific message mapping beyond preventing incomplete continuation history.

## Decisions

1. Treat pending external calls as unresolved step state.

   When a no-executor tool call is encountered, the step must finish and return the assistant tool-call history to the caller. The system must not start the next provider step even if earlier calls in the same batch produced server-side results, because the assistant message would otherwise contain a tool call without a matching result.

   Alternative considered: execute earlier server-side calls and continue with partial results. This preserves more eager execution but creates invalid provider history for providers that enforce tool result pairing.

2. Treat pending approvals as unresolved step state for the whole batch.

   If any call in a step requires approval, the generation call must return approval requests and must not continue. The recorded response history must not include unrelated executable tool calls unless those calls are also resolved in the same response history.

   Alternative considered: execute non-approval calls while returning approval requests. This is only safe if response messages include results for all executed calls and keep pending approval calls unresolved without continuation. It is more complex and unnecessary for this corrective change.

3. Centralize continuation checks around resolved tool-call coverage.

   `LanguageModelImpl` should use an explicit signal from tool evaluation/execution to decide whether continuation is allowed. The loop should not infer completeness only from the presence of at least one tool result.

   Alternative considered: inspect warnings such as `external-tool-pending` directly in the loop. That is brittle because warnings are user-facing diagnostics, not control-flow state.

## Risks / Trade-offs

- Pending mixed batches may execute fewer server-side tools in the first response than before -> This is intentional to preserve valid AI tool history; callers can resume once external results or approvals are supplied.
- Existing tests may assert eager execution in mixed batches in the future -> Add explicit regression tests documenting that incomplete tool-call batches stop the loop.
- Stream behavior must remain progressive -> Continue emitting provider deltas and completed tool-call/approval parts before finish, but do not start the next streamed provider step until all current-step tool calls are resolved.
