## Context

AI Foundation already exposes provider-neutral generation requests, streaming results, tools, structured output, reasoning parts, and step control. The remaining operational gap is that callers cannot observe the lifecycle of a generation through stable callbacks, cannot attach request metadata/context to those lifecycle events, and cannot configure cancellation or timeout behavior as part of the request contract.

provider-neutral AI API v6 exposes event callbacks for `generateText`, `streamText`, `embed`, and `embedMany`, including generation start, step start, tool call start/finish, step finish, and final finish. Its generation call options also include timeout and abort signal. Halo should align with those semantics while staying idiomatic for Java/Reactor and preserving the public API boundary.

## Goals / Non-Goals

**Goals:**

- Add provider-neutral Java API types for lifecycle callbacks and lifecycle event payloads.
- Add request-scoped metadata/context that callbacks can read without leaking provider-specific classes.
- Add request-scoped timeout controls for total generation, per-step provider calls, and tool execution.
- Add request-scoped cancellation control that can be triggered by SDK callers and checked by generation loops.
- Apply lifecycle callbacks to both `generateText` and `streamText`, including multi-step tool loops.
- Add compatible lifecycle coverage for embedding calls where the current API shape allows it.
- Keep callback, cancellation, and Java-only control fields out of OpenAPI and generated frontend DTOs.

**Non-Goals:**

- No telemetry exporter, metrics backend, tracing integration, or DevTools UI.
- No middleware system such as model wrapping or transform pipelines.
- No retry policy redesign.
- No provider registry, MCP integration, or non-text model work.
- No compatibility fields for old step-count or callback shapes.

## Decisions

### Lifecycle callbacks are request-scoped Java callbacks

Add Java callback interfaces in `api/`, referenced from `GenerateTextRequest` and advanced `EmbeddingRequest` as transient fields. A request can define a single `GenerationLifecycle` callback object or separate callback functions through a builder-style aggregate.

Rationale: request-scoped callbacks match provider-neutral AI API's per-call model and avoid global observers that would need registration, lifecycle management, and permissions. Transient fields keep Java-only callbacks out of OpenAPI.

Alternative considered: application-wide event publisher. That may be useful later for telemetry, but it would make per-call behavior and tests harder to reason about.

### Event payloads are immutable provider-neutral DTOs

Introduce event DTOs such as `GenerationStartEvent`, `GenerationStepStartEvent`, `GenerationToolCallStartEvent`, `GenerationToolCallFinishEvent`, `GenerationStepFinishEvent`, `GenerationFinishEvent`, and `GenerationErrorEvent`. They include model/provider metadata already exposed by the request/result layer, step index, messages, tools, active tools, tool call/result/error, usage, warnings, total usage, and caller metadata/context where available.

Rationale: immutable payloads prevent callback mutation from changing generation state. Provider-neutral payloads preserve the `api/` module boundary.

Alternative considered: reuse `StepContext` for all callback payloads. That would be too narrow for tool start/finish and final finish events, and would mix step-control input with observability output.

### Callback failures are contained and reported as warnings

Lifecycle callbacks should not make a successful provider generation fail unless the caller explicitly uses cancellation or throws from a tool executor. Callback exceptions should be captured as warning entries and surfaced in the current step or final result where possible.

Rationale: observability hooks should be safe to add. A logging callback failure should not hide a valid model response.

Alternative considered: fail the generation on callback exceptions. That is stricter but makes monitoring code part of the critical path.

### Timeout is explicit and layered

Add a provider-neutral timeout config with total timeout, step timeout, and tool timeout. Total timeout bounds the whole request. Step timeout bounds each provider call. Tool timeout bounds each server-side tool executor call. Streaming should emit an error part when timeout occurs after the stream has started; final projections should fail with a typed timeout exception.

Rationale: provider-neutral AI API exposes timeout as call option, and real tool loops need different timeout scopes. Separate scopes let callers protect long-running tools without globally shrinking model generation time.

Alternative considered: one timeout duration. Simpler, but it cannot express common cases like "allow two minutes total, but each tool must finish in ten seconds."

### Cancellation is represented by a small provider-neutral token

Add `CancellationToken`/`CancellationSource` style API types that can be passed into requests. `LanguageModelImpl` should check cancellation before starting generation, before each provider step, before tool execution, and before continuation. Stream subscribers can still cancel through Reactor subscription disposal; explicit cancellation gives non-streaming and service-layer callers the same control.

Rationale: Java does not have `AbortSignal`, and Reactor cancellation alone is not convenient for all SDK callers. A small token avoids importing framework-specific cancellation types into `api/`.

Alternative considered: only document `Disposable.dispose()`. That is useful for streams, but it does not give `generateText()` callers a first-class request option.

### Stream result cancellation follows existing full-stream semantics

When cancellation or timeout occurs after `fullStream()` has emitted `start`, the stream should close open lifecycle blocks, emit a safe `error` or `abort` part, and terminate. `textStream()` should stop emitting text. Final projections should fail with the typed cancellation/timeout exception rather than returning a partial result as success.

Rationale: this preserves the current distinction between full protocol events and final result projections.

Alternative considered: complete final projections with partial results. That would make cancellation ambiguous and easy to mistake for a successful answer.

## Risks / Trade-offs

- [Risk] Callback execution can slow down generation. -> Mitigation: invoke callbacks sequentially but document they should be lightweight; allow asynchronous `Mono<Void>` callbacks so callers can schedule work explicitly.
- [Risk] Timeout behavior may vary by provider HTTP client. -> Mitigation: enforce timeout at Reactor boundaries and report a typed timeout even if the provider cannot physically cancel the remote request immediately.
- [Risk] Cancellation during tool execution may depend on tool executor cooperation. -> Mitigation: check cancellation before tool start and apply tool timeout/cancellation around the executor `Mono`.
- [Risk] Too many callback types can make the API noisy. -> Mitigation: expose a single lifecycle aggregate with default no-op methods and keep individual event DTOs small.
- [Risk] Callback exceptions could hide operational issues if only warnings are used. -> Mitigation: warnings include stable codes and safe messages, and tests verify they appear in results.

## Migration Plan

The plugin is unreleased, so request DTOs can change directly. Implement the API types first, then wire `LanguageModelImpl`, then embedding calls, then docs/tests. Regenerate API clients to confirm Java-only fields stay out of HTTP schemas. Rollback is reverting this change before release.

## Open Questions

- Should callback exceptions always become warnings, or should callers be able to opt into fail-fast callback behavior?
- Should timeout/cancellation errors use a shared `AiFoundationException` subtype hierarchy for both language and embedding calls?
- Should callback metadata include sanitized provider raw diagnostics when `include` options are introduced later?
