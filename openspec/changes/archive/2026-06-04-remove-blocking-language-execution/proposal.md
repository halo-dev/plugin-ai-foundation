## Why

`LanguageModel.streamText` currently executes request-scoped tools and generation lifecycle callbacks by calling `.block()` on `Mono` values. Halo plugins can naturally implement tools with `ReactiveExtensionClient` APIs, so blocking those callbacks on a WebFlux event-loop thread can fail with `block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-*`.

This plugin is still unreleased, so now is the right time to align the implementation with the public SDK's Reactor contracts instead of preserving an internally synchronous orchestration model.

## What Changes

- Replace blocking language-model tool execution with Reactor-native orchestration for both streaming and non-streaming generation paths.
- Replace blocking tool-call repair callback execution with Reactor-native sequencing and error handling.
- Replace blocking generation lifecycle callback invocation with Reactor-native sequencing while preserving the current "safe observer" warning behavior.
- Keep stream part ordering, tool result history, approval resumption, timeout, cancellation, and warning semantics consistent with the existing tool-flow requirements.
- Audit language SDK implementation paths for remaining `block()`, `blockFirst()`, and `blockLast()` usage and keep any unavoidable blocking isolated to bounded-elastic execution.
- Backend-only change. No console UI behavior is expected to change.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `streaming-tool-calls`: server-side tool execution, repair callbacks, approval resumption, and continuation steps must run without blocking Reactor non-blocking threads.
- `generation-lifecycle-controls`: lifecycle callbacks must be invoked without blocking Reactor non-blocking threads while remaining safe observers.
- `language-model-maintainability`: language generation orchestration must remain centralized and maintainable after converting the tool and lifecycle flow to Reactor-native execution.

## Non-goals

- Do not change public tool, lifecycle, approval, stream part, or message history API shapes unless implementation constraints make a small source-compatible adjustment unavoidable.
- Do not add new UI controls or console workflows.
- Do not change provider-specific behavior, model discovery, default model slots, or embedding request semantics.

## Impact

- Affected implementation areas:
  - `app/src/main/java/run/halo/aifoundation/service/language/LanguageModelImpl.java`
  - `app/src/main/java/run/halo/aifoundation/service/language/LanguageModelGenerationRun.java`
  - `app/src/main/java/run/halo/aifoundation/service/language/tool/LanguageModelToolExecutor.java`
  - `app/src/main/java/run/halo/aifoundation/service/language/tool/ToolStepCoordinator.java`
- Affected SDK contracts:
  - `ToolExecutor.execute(...)` already returns `Mono<Object>` and should be treated as asynchronous.
  - `ToolCallRepairCallback.repair(...)` already returns `Mono<ToolCallRepairResult>` and should be treated as asynchronous.
  - `GenerationLifecycle` methods already return `Mono<Void>` and should be treated as asynchronous observers.
- Tests must cover a tool executor that performs asynchronous Halo-style reactive work and is consumed through `streamText(...).fullStream()` without event-loop blocking.
