## Context

AI Foundation already exposes the main provider-neutral surfaces: `generateText`, `streamText`, request-scoped tools, reasoning parts, structured output, warnings, usage, metadata, and steps. The remaining gap is behavioral depth. Some fields exist but do not yet provide the same level of runtime semantics that provider-neutral AI API callers expect, especially around stream result composition, structured streaming, tool execution context, warnings, and validation error details.

This change spans the published `api` module, `app` implementation, generated OpenAPI client, Console test workbench, and docs. The plugin is unreleased, so the public Java API can change directly without compatibility adapters.

## Goals / Non-Goals

**Goals:**
- Make `streamText` return a result object that can expose multiple stream views and final async values, not only a raw full stream.
- Implement structured streaming for partial object/json snapshots and validated array elements in a way that is genuinely useful to callers.
- Make tool execution context-rich enough for production callers to correlate tool execution with stream events and conversation state.
- Make warnings and structured output validation errors deterministic, typed, and useful for debugging.
- Preserve the Halo-owned stream protocol and Console SSE behavior through `fullStream`.

**Non-Goals:**
- Add image, speech, transcription, video, rerank, or MCP tool client APIs.
- Implement third-party UI stream protocol stream protocol compatibility.
- Add full agent loop control (`prepareStep`, `activeTools`, `stopWhen`) unless it is needed to preserve existing `maxSteps` behavior.
- Add compatibility overloads for the old `Flux<TextStreamPart>` `streamText` signature.

## Decisions

### Decision: Replace direct stream return with `StreamTextResult`

`LanguageModel.streamText(request)` will return `StreamTextResult` instead of `Flux<TextStreamPart>`.

The result object will expose:
- `Flux<TextStreamPart> fullStream()`
- `Flux<String> textStream()`
- `Flux<Object> partialOutputStream()`
- `Flux<Object> elementStream()`
- `Mono<Object> output()`
- `Mono<GenerateTextResult> result()`

Rationale: provider-neutral AI API's `streamText` returns a rich result with `textStream`, `fullStream`, `partialOutputStream`, `elementStream`, complete `output`, and final metadata. A direct `Flux<TextStreamPart>` cannot represent these surfaces without either re-consuming the stream or hiding important state.

Alternative considered: keep `Flux<TextStreamPart>` and add helper utilities. Rejected because it would keep the public API shape convenient for SSE but weak for Java callers, and it would make partial/element output bolted on rather than first-class.

### Decision: Make `fullStream` the source stream and derive other views from one shared execution

Implementation should create a single generation execution and multicast/replay necessary events to derived views. `textStream`, `partialOutputStream`, `elementStream`, `output`, and `result` must not trigger separate provider calls.

Rationale: provider-neutral AI API streams are multiple views over one generation. Multiple subscriptions must not duplicate provider requests or tool execution.

Alternative considered: derive each stream by calling `streamText` again. Rejected because it would duplicate model calls and tools.

### Decision: Structured partial and element streams are client-facing streams, not final output parts

The full stream remains Halo's lifecycle stream. It may include optional Halo-owned partial/element stream parts when useful for SSE/UI, but final parsed `output` is not emitted as a `finish-step` or final content part. The complete parsed output is exposed through `StreamTextResult.output()` and `GenerateTextResult.output`.

Rationale: This keeps the earlier decision that final structured results belong to result objects, while still implementing external provider-neutral partial and element streaming.

Alternative considered: put final output into `finish-step`. Rejected because it duplicates final result state and makes the stream protocol harder to reason about.

### Decision: Partial output is best-effort and final output is authoritative

`partialOutputStream` emits parsed JSON snapshots only when the accumulated text can be parsed into a safe partial value. Partial snapshots are not a substitute for final schema validation. `elementStream` emits only complete array elements that pass element schema validation.

Rationale: provider-neutral AI API differentiates partial output from complete validated array elements. Halo should avoid claiming a partial object is fully schema-valid while still enabling progressive UI.

Alternative considered: validate every partial snapshot against the complete schema. Rejected because incomplete objects will naturally fail required-field validation.

### Decision: Tool execution receives a context object

`ToolExecutor` will receive a `ToolExecutionContext` that includes parsed input, tool call id, tool name, step index, current request messages, provider metadata, and room for future cancellation/context fields.

Rationale: provider-neutral AI API forwards tool call id and messages to tool execution. Real tools need correlation and state, especially when streaming custom status or invoking nested model calls.

Alternative considered: add overloaded executor methods. Rejected because the plugin is unreleased and compatibility code is not needed.

### Decision: Warnings are part of the behavior contract

Provider adapters and shared mapping code must emit stable warnings for unsupported, ignored, or downgraded options. Examples include JSON Schema downgraded to JSON object mode, strict schema requested but not enforced by provider, unsupported sampling settings, unsupported tool choice modes, and ignored input examples.

Rationale: Silent downgrade is the main way an SDK becomes only shape-compatible. Warnings let callers decide whether a fallback is acceptable.

Alternative considered: fail fast for every unsupported option. Rejected because provider-neutral AI API often reports provider limitations as warnings when generation can still proceed.

## Risks / Trade-offs

- `StreamTextResult` requires careful sharing to avoid duplicated upstream calls -> use a single shared execution and add tests that subscribe to multiple result views.
- Partial JSON parsing can be fragile with arbitrary model text -> treat partial output as best-effort, keep final validation authoritative, and never emit unsafe parse failures as fatal unless final validation fails.
- Adding context to `ToolExecutor` is breaking -> acceptable because the plugin is unreleased and avoids long-term compatibility wrappers.
- Provider support varies widely -> centralize warning generation around provider option mapping and add tests for known downgrade paths.
- Console SSE still needs a simple stream -> keep endpoint behavior on `fullStream()` and do not expose partial streams in the workbench unless the UI explicitly needs them.
