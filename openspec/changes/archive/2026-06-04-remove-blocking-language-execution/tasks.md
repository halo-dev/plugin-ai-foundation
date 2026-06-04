## 1. Baseline And Regression Coverage

- [x] 1.1 Add or identify focused tests for streaming server-side tool execution that assert stream part ordering and final response history.
- [x] 1.2 Add a regression test where a streamed server-side tool executor returns an asynchronous `Mono` and verify `fullStream()` completes without Reactor non-blocking-thread blocking errors.
- [x] 1.3 Add a regression test where `ToolCallRepairCallback.repair(...)` returns an asynchronous `Mono` and preserves repaired-call warnings and execution behavior.
- [x] 1.4 Add a regression test where generation lifecycle callbacks return asynchronous `Mono<Void>` values and callback failures remain warnings.

## 2. Lifecycle Callback Orchestration

- [x] 2.1 Convert `LanguageModelGenerationRun` lifecycle invocation helpers from blocking `void` execution to Reactor-composed `Mono` execution.
- [x] 2.2 Preserve callback ordering for start, step-start, tool-call-start, tool-call-finish, approval-request, step-finish, finish, and error events.
- [x] 2.3 Preserve safe-observer behavior by converting callback failures into generation warnings without failing generation where the current behavior continues.

## 3. Tool Execution Orchestration

- [x] 3.1 Convert `LanguageModelToolExecutor.execute(...)` to return `Mono<ToolExecutionBatch>`.
- [x] 3.2 Convert tool-call repair handling to compose `ToolCallRepairCallback.repair(...)` without blocking.
- [x] 3.3 Compose `ToolExecutor.execute(...)` without blocking while preserving tool timeout, cancellation checks, output schema validation, lifecycle callbacks, and safe tool error behavior.
- [x] 3.4 Preserve deterministic sequential tool execution and the current stop-after-error or pending-external behavior.

## 4. Tool Step Coordination And Generation Loops

- [x] 4.1 Convert `ToolStepCoordinator.resolve(...)` to return `Mono<ToolStepResolution>` when execution may be asynchronous.
- [x] 4.2 Update `LanguageModel.streamText(...)` tool loops to compose tool-step resolution before emitting tool result, tool error, finish-step, finish, or continuation-step stream parts.
- [x] 4.3 Update approval-resumption handling so approved tool execution composes asynchronously before provider continuation.
- [x] 4.4 Update non-streaming `generateText(...)` orchestration to use the same Reactor-native tool coordinator while keeping synchronous provider calls isolated on bounded-elastic execution.

## 5. Blocking Audit And Validation

- [x] 5.1 Audit `api/src/main/java` and `app/src/main/java` for remaining `block()`, `blockFirst()`, and `blockLast()` calls.
- [x] 5.2 Remove unsafe Reactor blocking from tool execution, tool repair, and generation lifecycle paths.
- [x] 5.3 Keep any unavoidable synchronous provider calls behind `Schedulers.boundedElastic()` and document why they remain.
- [x] 5.4 Run focused language-model tests covering tool execution, repair, lifecycle callbacks, stream protocol ordering, and approval resumption.
- [x] 5.5 Run `./gradlew compileJava`, relevant backend tests, and `openspec validate remove-blocking-language-execution --strict`.
