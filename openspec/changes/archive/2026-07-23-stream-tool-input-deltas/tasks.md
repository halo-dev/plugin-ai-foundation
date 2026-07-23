## 1. Public Java Contracts

- [x] 1.1 Add typed `tool-input-end` and `tool-input-error` full-stream parts and update stream-part factories, visitors, serialization, and protocol validation tests.
- [x] 1.2 Add immutable provider-neutral input-start, input-delta, and input-available callback context types with request context, message snapshots, provider metadata, and cancellation.
- [x] 1.3 Extend `ToolDefinition` with optional reactive `onInputStart`, `onInputDelta`, and `onInputAvailable` callbacks and JavaDoc their ordering, timeout, cancellation, and failure semantics.
- [x] 1.4 Extend `ToolExecutionContext` and related approval context construction with immutable request context and collection snapshots without exposing Spring AI types.
- [x] 1.5 Remove `toolName` from the Java `ToolInputDeltaChunk` contract and update compile-time/API tests for the new canonical shape.

## 2. Provider-Native Stream Preservation

- [x] 2.1 Add the app-internal `ProviderStreamingChatModel` and sealed provider stream-part model plus a final-only adapter for generic Spring AI `ChatModel` instances.
- [x] 2.2 Add the replaceable OpenAI-compatible `StreamDialect` boundary and implement the standard appendable Chat Completions dialect.
- [x] 2.3 Refactor `OpenAiCompatibleChatModel` so one raw SSE subscription feeds both incremental provider stream parts and the existing completed `ChatResponse` behavior.
- [x] 2.4 Implement per-index tool-call accumulation with stable fallback ids, immutable published ids, late-name buffering, isolated interleaved state, and unnamed-call protocol failure.
- [x] 2.5 Track `UNKNOWN`, `DELTA_OBSERVED`, and `FINAL_ONLY` per response without adding provider metadata capability flags or cross-request negative caching.
- [x] 2.6 Add raw SSE fixture tests for standard deltas, one complete fragment, final-only calls, late id/name, missing-id fallback, and interleaved calls.
- [x] 2.7 Add a cumulative-snapshot dialect fixture covering monotonic suffix extraction and regression fallback without generic content heuristics.
- [x] 2.8 Verify all provider types using `OpenAiCompatibleChatModel` use the standard dialect by default and Ollama/generic models remain final-only.

## 3. Tool Input Normalization and Error Boundaries

- [x] 3.1 Move completed tool input parsing and schema validation ahead of executor-specific, approval, and external-tool branches.
- [x] 3.2 Apply tool-call repair once to invalid known internal and external tools, revalidate repaired input, and preserve a stable repair warning.
- [x] 3.3 Emit authoritative input availability only after successful normalization and emit `tool-input-error` without approval, handoff, or execution when validation/repair fails.
- [x] 3.4 Treat a named inactive/unknown tool as a per-call input error while treating a never-named provider call as a step-terminating protocol error.
- [x] 3.5 Add non-streaming tests proving start/available callbacks run without delta and validation/repair precedes approval, external handoff, and execution.
- [x] 3.6 Add streaming tests for repaired input, repair failure, external tool validation, unknown versus missing names, and isolation of mixed/interleaved calls.

## 4. Backpressured Tool Input Lifecycle

- [x] 4.1 Route provider stream parts through the language runtime and emit start, ordered appendable deltas, end, and authoritative completed call in canonical order.
- [x] 4.2 Compose `onInputStart` and `onInputDelta` before matching public parts and `onInputAvailable` after availability but before approval, external handoff, or execution.
- [x] 4.3 Serialize callback and event processing globally while retaining independent accumulation state for each tool-call index.
- [x] 4.4 Apply total/step timeouts and before/after cancellation checks to input callbacks while leaving tool timeout scoped to the executor.
- [x] 4.5 Make callback failure terminate generation and cancel downstream tool actions, distinct from warning-only generation lifecycle observers.
- [x] 4.6 Add lifecycle tests for ordering, Reactor backpressure, callback failure, timeout, cancellation, multi-call serialization, and single callback invocation across projections.
- [x] 4.7 Extend reusable stream protocol assertions for input start/delta/end/call, input-error ordering, final-only calls, and provider failure with an open input block.

## 5. Cancellable Single-Run Replay

- [x] 5.1 Implement a lazy serialized replay coordinator that stores one ordered event history and terminal result for all `StreamTextResult` projections.
- [x] 5.2 Replace `LanguageModelImpl.streamText()` Reactor `cache()` sharing with the coordinator without changing projection contents on normal completion.
- [x] 5.3 Reference-count active public projection subscriptions so cancellation of one projection preserves the run while the last cancellation stops provider and callback work.
- [x] 5.4 Persist cancellation as a replayable terminal state so late subscribers observe partial history and typed cancellation without reconnecting.
- [x] 5.5 Add race-focused tests for simultaneous projections, provider/callback/executor at-most-once behavior, no-subscriber laziness, one-subscriber cancellation, last-subscriber cancellation, and late replay after success, failure, or cancellation.

## 6. UI Message Wire and npm SDK

- [x] 6.1 Update Java UI Message projection and validators so delta chunks omit `toolName`, input-end is not projected, and input-error maps to the canonical safe error chunk.
- [x] 6.2 Update npm SDK tool chunk types and guards for the breaking delta shape and canonical input-error handling.
- [x] 6.3 Port dependency-free `fixJson` and `parsePartialJson` helpers with unit tests for incomplete objects, arrays, strings, escapes, primitives, and unrepairable input.
- [x] 6.4 Add private per-call raw input accumulation to the reducer and expose only best-effort parsed `input` while state is `input-streaming`.
- [x] 6.5 Implement reducer tolerance for delta-before-start error, duplicate-start reset, available-without-start, final overwrite, and interrupted streaming state without fabricated input error.
- [x] 6.6 Add UI Message projection/reducer integration tests proving wire order, no delta `toolName`, partial parse updates, authoritative overwrite, invalid input, and `onToolCall` only after availability.

## 7. Documentation and Verification

- [x] 7.1 Update Java SDK and `dev/ui-message-stream.md` documentation with callback order, real-delta versus final-only behavior, partial input semantics, error boundaries, and the breaking delta shape.
- [x] 7.2 Add an optional manual provider smoke-test guide for representative OpenAI-compatible providers without requiring live credentials in CI.
- [x] 7.3 Run targeted API/app tests and the complete Gradle test suite, fixing all regressions.
- [x] 7.4 Run npm SDK tests, lint, and type checks, and regenerate any generated API/client artifacts required by changed public fields.
- [x] 7.5 Run strict OpenSpec validation and `git diff --check`, then confirm every requirement scenario has automated coverage or an explicit manual verification note.
- [x] 7.6 Add a console workbench test unit that injects a lifecycle-aware tool, captures raw UI Message input events, distinguishes provider-native deltas from final-only input, and displays backend callback diagnostics.
