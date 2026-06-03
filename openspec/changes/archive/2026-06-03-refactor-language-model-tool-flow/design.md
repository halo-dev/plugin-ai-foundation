## Context

`LanguageModelImpl` has grown into the central implementation for provider calls, retries, timeouts, streaming protocol events, structured output, tool execution, external tools, approval, repair, response history, and result aggregation. The recent tool-flow work improved functional correctness but also made the class harder to reason about because streaming and non-streaming paths now carry similar tool-step state machines.

The goal of this refactor is to improve code quality using the same behavioral contract already covered by existing OpenSpec requirements and tests. This change is internal and backend-only.

## Goals / Non-Goals

**Goals:**

- Reduce `LanguageModelImpl` responsibility by extracting reusable tool-step orchestration.
- Centralize response message history assembly for tool calls, approval requests, tool results, and tool errors.
- Keep streaming and non-streaming tool-loop decisions behaviorally identical by sharing one resolution model.
- Split the largest language model tests into focused classes without reducing coverage.
- Preserve current public API, stream protocol, lifecycle callbacks, response message shapes, and provider-neutral behavior.

**Non-Goals:**

- Do not add new tool features.
- Do not change provider adapter contracts or generated OpenAPI code.
- Do not refactor unrelated provider configuration, model resolution, embedding, or frontend code.
- Do not attempt to fully rewrite `LanguageModelImpl`; keep extraction incremental and reviewable.

## Decisions

1. Extract `ToolStepCoordinator` for tool-step resolution.

   `ToolStepCoordinator` will wrap the existing `LanguageModelToolExecutor` and approval resolver behavior into a single step-level result. It should answer:
   - Which tool calls are recorded for this step?
   - Which approvals are pending?
   - Which tool results or errors were produced?
   - Which warnings were produced?
   - Is provider continuation allowed?

   Alternative considered: keep the current logic in `LanguageModelImpl` and only add helper methods. That lowers immediate churn but leaves streaming and non-streaming parity dependent on duplicated conditional logic.

2. Extract `GenerationMessageHistoryAssembler`.

   Response history construction is a core invariant for tool workflows: assistant tool calls must align with approval requests, tool results, or tool errors. A dedicated assembler makes that invariant explicit and reusable from both streaming and non-streaming result construction.

   Alternative considered: leave `appendApprovalMessages`, `appendToolMessages`, and `responseWithToolCalls` private in `LanguageModelImpl`. That keeps fewer files but preserves the current hidden coupling.

3. Split tests by behavior, not by implementation class.

   Focused tests should reflect user-observable behavior and protocol invariants:
   - basic generation and provider mapping
   - server-side tool loop
   - external tool flow
   - approval flow
   - tool repair flow
   - streaming protocol behavior
   - structured output and timeout/cancellation behavior

   Alternative considered: keep one large `LanguageModelImplTest` file and add nested classes. Nested classes improve grouping but do not reduce file size or navigation cost enough.

4. Keep extraction internal to `app`.

   New classes should remain under `app/src/main/java/run/halo/aifoundation/service/language` or subpackages. The public `api` module should not change because this is not a user-facing feature.

## Risks / Trade-offs

- [Risk] Refactor may accidentally change streaming/non-streaming parity -> Mitigation: move behavior behind shared coordinator, then run focused language model tests and full backend tests.
- [Risk] Test split can obscure git history or make helpers harder to share -> Mitigation: introduce shared test fixture/helper class in the same test package before moving tests.
- [Risk] Over-extraction can create thin classes with unclear value -> Mitigation: extract only repeated stateful responsibilities: tool-step resolution and message history assembly.
- [Risk] Existing uncommitted functional fixes may overlap with refactor -> Mitigation: preserve current behavior and do not revert unrelated workspace changes.
