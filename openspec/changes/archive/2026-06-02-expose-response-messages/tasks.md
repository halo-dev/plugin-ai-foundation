## 1. Public API

- [x] 1.1 Add provider-neutral `responseMessages` fields to `GenerateTextResult` and `GenerationStep`.
- [x] 1.2 Ensure response-message fields use existing `ModelMessage` and `ModelMessagePart` types without exposing provider-native classes.
- [x] 1.3 Add or update SDK ergonomics tests so public result types include response messages.

## 2. Non-Streaming Orchestration

- [x] 2.1 Accumulate assistant response messages for generated text, reasoning, tool calls, and pending approval requests.
- [x] 2.2 Accumulate tool response messages for executed tool results and tool errors.
- [x] 2.3 Accumulate consumed approval history when approved or denied approval responses are resolved before the provider call.
- [x] 2.4 Populate step-local response messages and top-level response messages in `GenerateTextResult`.

## 3. Streaming Orchestration

- [x] 3.1 Accumulate response messages in the shared streaming execution state without duplicating work across projections.
- [x] 3.2 Populate `StreamTextResult.result()` response messages after successful stream completion.
- [x] 3.3 Ensure `textStream()` remains answer-text-only and does not emit serialized response messages.
- [x] 3.4 Preserve response-message ordering for streamed tool calls, tool results/errors, approval requests, and continuation answers.

## 4. Console Test Workbench

- [x] 4.1 Replace local hidden approval/tool history reconstruction with returned response messages where the stream final result provides them.
- [x] 4.2 Keep approval approve/deny interactions sending real `tool-approval-response` messages and persisting consumed result/error history.
- [x] 4.3 Verify regenerated console test responses do not replay already consumed approvals or tools.

## 5. Documentation

- [x] 5.1 Update `dev/dev.md` to document appending `GenerateTextResult.responseMessages` for normal conversation persistence.
- [x] 5.2 Document tool-loop persistence with assistant tool-call and tool result/error response messages.
- [x] 5.3 Document approval continuation persistence, including caller-supplied approval responses plus returned consumed result/error history.
- [x] 5.4 Update documentation validation tests for the new response-message workflow.

## 6. Validation

- [x] 6.1 Add non-streaming tests for text-only, normal tool loop, failed tool loop, pending approval, approved approval, denied approval, and already-consumed replay prevention.
- [x] 6.2 Add streaming tests for response-message accumulation, multiple projection safety, approval request persistence, and consumed approval persistence.
- [x] 6.3 Run `openspec validate expose-response-messages --strict`.
- [x] 6.4 Run `./gradlew test`.
- [x] 6.5 Run `pnpm -C ui exec vue-tsc --build` if console workbench code changes.
- [x] 6.6 Run `git diff --check`.
