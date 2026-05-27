## Context

The text generation API now exposes provider-neutral request/result types, streaming tool calls, reasoning parts, structured output, and `StreamTextResult` projections. The remaining gap is behavioral fidelity: AI SDK Core treats multi-step generation as a controllable loop, exposes multiple final-result promises from one stream execution, and has a richer full-stream event vocabulary. Halo has the foundations, but some behavior is still embedded in implementation details rather than a clear contract.

This change spans the public Java SDK, Spring AI-backed service implementation, console test streaming endpoint, OpenAPI-generated UI client, and UI stream parser tests.

## Goals / Non-Goals

**Goals:**

- Add provider-neutral step control equivalent to AI SDK's `stopWhen` and `prepareStep`.
- Make `stopWhen` the only public continuation rule.
- Ensure all stream/result projections share one underlying model execution.
- Make full-stream lifecycle rules explicit and testable, including no overlapping text/reasoning/tool-input blocks.
- Add stream event types for sources, files, and incremental tool input where provider data exists.
- Improve `StreamTextResult` ergonomics with final-result convenience accessors.
- Preserve structured-output text streaming while validating completed output and completed array elements.

**Non-Goals:**

- No MCP, middleware, telemetry, image/video/speech/transcription, reranking, or provider registry expansion.
- No compatibility adapter for old stream ordering or old event names.
- No provider-specific public API types.

## Decisions

### Step control belongs in the public API module

Introduce `StopCondition`, `StepControl`, `StepContext`, `PrepareStepCallback`, and `PreparedStep` style types in `api/`. `GenerateTextRequest` will accept optional `stopWhen` and `prepareStep` fields. `stopWhen` is the only public continuation rule; omitted `stopWhen` means a single model step.

Alternative considered: keep step control internal to `LanguageModelImpl`. That would avoid API growth, but callers would still be unable to express real agent-loop behavior.

### `prepareStep` returns overrides, not mutation hooks

`prepareStep` will receive immutable context for the next step and return optional overrides: messages, tool choice, active tools, provider options, model settings, and stop condition. This keeps the implementation deterministic and easier to test than allowing callbacks to mutate the in-flight request.

Alternative considered: pass a mutable builder into the callback. That is shorter for callers, but makes callback side effects harder to reason about.

### Full stream events are normalized through one protocol layer

`LanguageModelImpl` should map provider events into a canonical `TextStreamPart` stream through a single normalizer that tracks currently open blocks. The normalizer closes incompatible open blocks before opening a new block and emits protocol errors for invalid transitions that cannot be repaired.

Alternative considered: keep lifecycle management inline in each stream path. That already proved fragile because normal text, reasoning, tools, and structured streaming drifted apart.

### Tool input streaming is best-effort but typed

Add `tool-input-start` and `tool-input-delta` event types. Providers that only expose complete tool calls can continue emitting `tool-call` without input deltas. Providers that expose incremental arguments should emit input start/delta before the final `tool-call`.

Alternative considered: synthesize input deltas from complete tool calls. That would be misleading because callers could mistake synthesized chunks for provider streaming behavior.

### `StreamTextResult` convenience accessors derive from `result()`

Add convenience `Mono`/`Flux` accessors that project from the shared final result and cached full stream. They must not subscribe to a fresh provider invocation. The implementation should use Reactor sharing/caching so consuming `textStream()`, `fullStream()`, and `result()` together is safe.

Alternative considered: leave callers to call `result()` and unpack everything manually. That works, but is noticeably less aligned with AI SDK Core's promise-based stream result.

### Structured output remains text-first

Structured output continues to stream model text as JSON text. `partialOutputStream()` emits best-effort partial values without final schema validation. `elementStream()` emits only complete array elements that validate against the element schema. The final `output()` and `GenerateTextResult.output` are validated parsed values.

Alternative considered: add dedicated `output-*` stream parts. That was rejected earlier because AI SDK's `streamText` structured output is exposed as result projections, not extra full-stream parts.

## Risks / Trade-offs

- Step callbacks can introduce complex loops -> cap all execution by a resolved stop condition and keep a hard maximum default.
- Provider event support varies -> unsupported source/file/tool-input streaming must degrade cleanly without fake events.
- Reactor sharing can be subtle -> add tests that consume multiple result projections and assert one provider execution.
- Protocol normalization can hide provider bugs -> emit warning/error metadata for repaired or invalid transitions where useful.
- Public API grows quickly -> keep types small and provider-neutral, with JavaDoc examples for each entry point.

## Migration Plan

This plugin is unreleased, so implementation can update public API types directly.

1. Add API contracts and JavaDoc.
2. Update `LanguageModelImpl` step orchestration and stream normalization.
3. Refresh endpoint schema and UI generated client if event/request shapes change.
4. Update console test parser only for surfaced event types.
5. Validate with backend tests, UI parser tests, generated API client checks, and OpenSpec validation.

Rollback is removing this change before release; no compatibility path is required.

## Open Questions

- Which providers currently expose incremental tool argument chunks through Spring AI, and which can only emit final tool calls?
- Should source/file stream parts be displayed in the console test UI immediately, or parsed and kept available for a later UI pass?
