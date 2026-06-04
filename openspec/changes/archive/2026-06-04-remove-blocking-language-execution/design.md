## Context

The language SDK already exposes Reactor types for the extension points that may perform asynchronous work:

- `ToolExecutor.execute(...)` returns `Mono<Object>`.
- `ToolCallRepairCallback.repair(...)` returns `Mono<ToolCallRepairResult>`.
- `GenerationLifecycle` callbacks return `Mono<Void>`.

The current language implementation still invokes those callbacks through `.block()` in the tool executor and lifecycle runner. That implementation can fail when `LanguageModel.streamText(...)` is consumed from Halo's WebFlux request handling and a tool internally uses APIs such as `ReactiveExtensionClient.list(...)`, because the blocking call may run on a `reactor-http-nio-*` thread.

The embedding implementation already composes lifecycle callbacks and bounded-elastic provider calls as Reactor chains, so this change should bring the language implementation to the same model rather than changing embedding behavior.

## Goals / Non-Goals

**Goals:**

- Remove `.block()`, `blockFirst()`, and `blockLast()` from language-model tool execution, tool-call repair, and generation lifecycle callback paths.
- Keep tool execution ordered and deterministic for existing single-threaded semantics.
- Preserve stream protocol ordering, message history, approval resumption, timeout, cancellation, warning, and safe-error behavior.
- Keep unavoidable synchronous provider calls isolated behind bounded-elastic scheduling.
- Add regression coverage for stream tools that perform asynchronous Halo-style reactive work.

**Non-Goals:**

- Do not redesign public tool, lifecycle, approval, or stream part APIs.
- Do not change provider adapter behavior except where bounded-elastic scheduling is needed for synchronous provider calls.
- Do not change embedding orchestration unless a new audit finds an equivalent blocking bug.
- Do not introduce UI changes.

## Decisions

### Decision: Convert tool orchestration APIs to Reactor-native results

`LanguageModelToolExecutor.execute(...)` should return `Mono<ToolExecutionBatch>` instead of `ToolExecutionBatch`. `ToolStepCoordinator.resolve(...)` should likewise return `Mono<ToolStepResolution>` when it may execute tools. Call sites in streaming and non-streaming generation should compose these monos instead of blocking.

Alternative considered: wrap the current blocking tool execution in `subscribeOn(Schedulers.boundedElastic())`. That would fix the immediate Netty-thread failure, but it would keep the implementation semantically synchronous, make cancellation weaker, and hide the mismatch with the public `Mono` contracts. Since the plugin is unreleased, a Reactor-native refactor is preferable.

### Decision: Preserve sequential tool execution by default

When a provider returns multiple executable tool calls, the implementation should continue processing them in deterministic order with `concatMap` or equivalent chaining. The current implementation stops the batch after the first error or external pending condition; the Reactor version should preserve that behavior unless a spec explicitly changes it later.

Alternative considered: run independent tools with `flatMap` for parallelism. That would be faster for some workloads, but it can reorder lifecycle events, tool results, and follow-up history. Parallel tools can be introduced later behind an explicit policy.

### Decision: Make lifecycle callbacks asynchronous safe observers

`LanguageModelGenerationRun` should expose lifecycle methods that return `Mono<Void>` or a small lifecycle result carrying warnings. Callback failures should still become provider-neutral warnings and should not fail generation when the old behavior would continue.

Alternative considered: fire-and-forget callback subscriptions. That would avoid blocking, but it would break ordering guarantees and can lose warnings. Lifecycle callbacks should remain part of the generation chain.

### Decision: Split synchronous provider calls from asynchronous orchestration

Spring AI provider `call(...)` APIs that are synchronous should stay in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`. Streaming provider APIs should remain streaming, while post-step completion logic should compose asynchronous tool and lifecycle work without blocking.

Alternative considered: rewrite provider adapters to be fully non-blocking. That is outside this change and not required to fix the Halo tool callback failure.

### Decision: Keep public API source-compatible unless implementation demands otherwise

The public SDK already models the affected extension points as Reactor values, so the expected implementation should not require public signature changes. Internal methods may change from synchronous return values to `Mono` return values.

## Risks / Trade-offs

- Tool event ordering could regress during the refactor -> Add focused stream tests that assert `tool-call`, lifecycle callback, `tool-result/tool-error`, `finish-step`, and continuation-step ordering.
- Callback errors could accidentally terminate generation -> Preserve the current warning behavior with `onErrorResume` at each callback boundary.
- Non-streaming generation may become harder to read if converted all at once -> Keep orchestration helpers small and behavior-focused, and use dedicated tests around tool loops.
- Cancellation semantics may change from blocking interruption to Reactor cancellation -> Add tests for subscriber cancellation and cancellation tokens where existing hooks can observe cancellation.
- Total timeout and per-tool timeout might wrap the wrong scope -> Keep tool timeout inside each tool executor chain and total timeout around the public generation chain.

## Migration Plan

1. Convert lifecycle invocation to Reactor-returning helpers and update language generation call sites.
2. Convert tool repair and tool execution to `Mono<ToolExecutionBatch>`.
3. Convert tool-step coordination to return `Mono<ToolStepResolution>`.
4. Update streaming generation to compose tool resolution before emitting post-tool stream parts.
5. Update non-streaming generation to compose the same tool-step coordinator, using bounded-elastic only for synchronous provider calls.
6. Audit production language code for remaining `.block()`, `blockFirst()`, and `blockLast()` calls.
7. Add regression tests for asynchronous tool execution and lifecycle callbacks that internally use Reactor APIs.

Rollback is not expected to need compatibility shims because the plugin is unreleased. If implementation risk is discovered, the change can be reverted before release without data migration.
