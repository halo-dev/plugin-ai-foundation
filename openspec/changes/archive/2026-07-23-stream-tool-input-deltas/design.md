## Context

AI Foundation already exposes Halo-owned text, reasoning, tool, and UI Message stream parts, but `LanguageModelImpl` currently consumes Spring AI `ChatModel.stream(Prompt)` responses whose tool calls are complete objects. `OpenAiCompatibleChatModel` accumulates raw SSE tool fragments internally and only exposes the completed call, so the runtime cannot publish the reserved incremental tool-input lifecycle.

The same full stream backs `fullStream()`, `textStream()`, `result()`, and other projections through Reactor `cache()`. This prevents duplicate provider calls, but an unbounded connected cache does not cancel the upstream when the last projection subscriber leaves. New backpressured tool-input callbacks make that leak observable because provider and callback work can continue without consumers.

The implementation spans the published Java API, app-internal provider adapters and runtime, the canonical UI Message wire contract, and the npm SDK reducer. Public contracts must remain provider-neutral and must not expose Spring AI or provider-native types.

## Goals / Non-Goals

**Goals:**

- Preserve real provider-native tool argument fragments and expose start, delta, end, final availability, and input-error states in deterministic order.
- Follow AI SDK v6's UI behavior: accumulate raw input privately, expose best-effort partial parsed input while streaming, and replace it with authoritative final input.
- Add per-tool input callbacks that are ordered, backpressured, cancellable, and fail the generation before invalid or unobserved input can execute.
- Validate and optionally repair every known tool call before approval, server execution, or external handoff.
- Share one lazy generation among all `StreamTextResult` projections while allowing last-subscriber cancellation to stop upstream work and preventing late subscribers from re-running it.
- Support the standard OpenAI Chat Completions stream across current OpenAI-compatible providers without assuming provider identity implies capability.

**Non-Goals:**

- Fabricating delta events from a completed tool call.
- Advertising static per-provider tool-delta support or caching a negative capability result across calls.
- Implementing an Ollama-specific progressive argument protocol without an upstream contract.
- Exposing raw incomplete JSON as a public `ToolPart` field.
- Adding `onInputEnd`, a separate callback timeout, or persisted console configuration.
- Migrating to OpenAI Responses API.

## Decisions

### 1. Introduce an app-internal provider streaming SPI

The app will use an internal `ProviderStreamingChatModel` contract whose `streamParts(Prompt)` returns ordered provider-neutral parts:

```java
interface ProviderStreamingChatModel {
    Flux<ProviderStreamPart> streamParts(Prompt prompt);
}

sealed interface ProviderStreamPart {
    record ChatResponsePart(ChatResponse response) implements ProviderStreamPart {}
    record ToolInputStartPart(int index, String id, String name) implements ProviderStreamPart {}
    record ToolInputDeltaPart(int index, String inputTextDelta) implements ProviderStreamPart {}
    record ToolInputEndPart(int index) implements ProviderStreamPart {}
}
```

`OpenAiCompatibleChatModel` will implement this SPI while retaining its normal `ChatModel` behavior. Models without the SPI use an adapter that emits only `ChatResponsePart`, preserving final-only behavior.

This is preferred over attaching data to Spring AI metadata because metadata is not an ordered event channel and would couple a public runtime contract to provider implementation details. The SPI remains in `app` and does not become a provider capability advertised by `AiProviderType`.

### 2. Use replaceable stream dialects and evidence-based runtime observation

OpenAI-compatible transport will delegate SSE interpretation to an internal `StreamDialect`. The default dialect implements standard OpenAI Chat Completions semantics, where `choices[].delta.tool_calls[].function.arguments` is appendable incremental text.

Each call begins in `UNKNOWN`. Observing a valid fragment moves that call to `DELTA_OBSERVED`; reaching a completed call without one is `FINAL_ONLY`. This state is diagnostic and controls only that call. There is no provider-type table, probing request, or cached negative capability.

A dialect for cumulative snapshots may emit only the suffix when the new snapshot has the previous snapshot as a prefix. If a snapshot rewrites or regresses, it stops emitting deltas for that call and waits for the final authoritative call. The default dialect does not use content heuristics. Provider-specific dialects are added only with captured fixtures proving different behavior.

### 3. Correlate streamed calls by provider index and freeze public identity

Tool fragments are accumulated independently by provider tool-call index. The first event for an index establishes a stable public `toolCallId`: use the provider id when already present, otherwise generate the existing deterministic fallback form. Once `tool-input-start` is published, the public id is immutable even if the provider supplies its real id later.

The tool name may arrive after the first argument fragment. The runtime buffers publication for that index until it knows the name, then publishes start followed by buffered deltas. Interleaved indices retain independent state, while a serialized event chain preserves global provider order and callback backpressure. A stream that completes without ever naming a call is a provider protocol error; no synthetic `unknown` name is invented.

### 4. Make full-stream input lifecycle explicit and keep the UI wire minimal

The Java full stream gains `tool-input-end` and `tool-input-error` parts. For an incremental valid call the order is:

```text
onInputStart -> tool-input-start
onInputDelta -> tool-input-delta   (repeated)
tool-input-end
tool-call / UI tool-input-available
onInputAvailable
approval or execution
```

The UI Message projection omits `tool-input-end`; `tool-input-available` or `tool-input-error` is the terminal input transition. `tool-input-delta` carries only `toolCallId` and `inputTextDelta`. Its tool name is obtained from the preceding start state. Start and available retain `toolName`.

For non-streaming generation, input callbacks run as start then available with no delta and no public synthetic incremental parts.

This matches AI SDK v6's practical UI protocol while retaining a complete Java full-stream lifecycle for backend observers.

### 5. Put three reactive input callbacks on `ToolDefinition`

`ToolDefinition` gains optional `onInputStart`, `onInputDelta`, and `onInputAvailable` callbacks returning `Mono<Void>`. Each receives an immutable provider-neutral context containing tool call id, tool name, step index, immutable messages, request context, provider metadata, and cancellation token. Delta context adds `inputTextDelta`; available context adds normalized final input.

Callbacks are composed with `concatMap` or an equivalent serialized Reactor chain. Start and delta callbacks complete before their corresponding public part is emitted. The available part is emitted before `onInputAvailable`, matching AI SDK v6; approval and execution wait for that callback. Callback failure is terminal and prevents approval or execution, unlike observational generation lifecycle callbacks whose failures become warnings.

Input callbacks are bounded by total and step timeouts because they are part of input production. Tool execution timeout starts only around the executor. Cancellation is checked before and after each callback, and the same cancellation token is visible to callback and execution contexts. There is no separate callback timeout.

### 6. Normalize, validate, and repair before all downstream tool handling

Completed input follows one common path for every tool name present in the active `ToolDefinition` set, whether or not that definition has a server executor:

1. Parse and validate against `inputSchema`.
2. If invalid and repair is configured, invoke repair once and validate the repaired input.
3. On success, emit authoritative `tool-call`/`tool-input-available`, preserve a stable repair warning when applicable, then invoke `onInputAvailable`.
4. Only then evaluate approval, execute on the server, or expose pending external work.

If validation or repair fails, emit `tool-input-error`; do not emit input-available, invoke `onInputAvailable`, request approval, or execute. A named call for a tool absent from the active definitions also emits an input error but does not terminate the entire stream, allowing other calls in the step to remain observable. A call that never supplies a name is instead a malformed provider stream and terminates the step.

This moves normalization ahead of executor-specific branches. It avoids treating external tools as schema-unchecked data and keeps approval predicates on validated input.

### 7. Parse partial JSON privately in the npm reducer

The npm SDK ports the small `parsePartialJson`/`fixJson` strategy used by AI SDK v6 instead of adding a dependency. The reducer maintains accumulated raw argument text in private reducer state per tool call. After every delta it attempts repair and parse; a failure yields `undefined` and never throws. The public dynamic tool part remains `input-streaming` and exposes only the best partial parsed `input`.

`tool-input-available` overwrites partial input with authoritative final input. Reducer tolerance follows AI SDK v6:

- delta without start is a protocol error;
- duplicate start resets the accumulated state;
- available without start is accepted and creates the final part;
- stream error or cancellation leaves a partially streamed tool part in `input-streaming`, with the outer stream carrying the error;
- no fabricated input-error or strict terminal cleanup is added.

### 8. Replace `cache()` with cancellable single-run replay

`StreamTextResult` will use an internal lazy replay coordinator rather than Reactor's unbounded `cache()`:

- the first projection subscriber starts generation once;
- all projections subscribe to the same ordered event log and terminal result;
- active projection subscriptions are reference-counted;
- cancellation of the last active subscriber cancels the provider subscription and any running input callback;
- the coordinator records a cancellation terminal state and preserves events already produced;
- a late subscriber replays the partial history and cancellation outcome without starting a new provider call;
- normal completion and failure remain replayable to late projection subscribers.

The coordinator starts nothing when there is no subscriber. Projection-internal subscriptions must be deduplicated so one public projection does not inflate the active count. A bounded request already has generation limits, but the coordinator remains responsible for releasing its log after the result object becomes unreachable.

This preserves the existing at-most-once contract and strengthens cancellation propagation. `replay().refCount()` alone was rejected because after ref-count cancellation a late subscriber can reconnect and re-run the provider.

### 9. Verify contracts with deterministic fixtures, not live providers

Raw SSE/dialect fixtures cover standard incremental calls, a single complete fragment, final-only calls, late id/name, interleaved calls, missing-id fallback, cumulative snapshots, and snapshot regression. Runtime tests cover callback order and backpressure, non-stream calls, failure/cancellation, validation and repair for internal and external tools, unknown versus missing names, and multi-projection replay. npm tests cover wire shape and reducer tolerance.

Live OpenAI, DeepSeek, Kimi, or other provider credentials are not required in CI. An optional manual smoke-test guide may document verification against selected providers.

### 10. Dogfood the complete path in the console workbench

The model test workbench adds an opt-in `halo_tool_input_stream_test` tool and a dedicated example
prompt. Its transport observes canonical input chunks before the npm reducer consumes them, records
the per-call start/delta/available/error sequence, and labels calls with deltas as provider-native or
calls with only availability as final-only. The tool also records its Java `onInputStart`,
`onInputDelta`, and `onInputAvailable` callbacks and returns that backend lifecycle in its normal
tool output.

This is an in-memory test control, not provider capability configuration. It gives one manual run
two independent observations of the same request: raw browser wire events and backend callback
events. It does not synthesize deltas, so final-only providers remain visibly final-only.

## Risks / Trade-offs

- **[Raw SSE behavior differs among nominally OpenAI-compatible providers]** → Isolate interpretation behind dialect fixtures and fall back to final availability when a reliable append-only sequence is absent.
- **[Late tool names require buffering fragments]** → Bound buffering by existing response/step limits and fail malformed unnamed calls at step completion.
- **[Backpressured callbacks can slow token consumption]** → Make ordering explicit, apply total/step timeouts, and document callbacks as part of generation rather than passive observers.
- **[Removing `toolName` from delta chunks breaks draft consumers]** → Change Java and npm types together, regenerate affected contracts, and document that start establishes identity; the plugin is unreleased.
- **[Replay coordination is concurrency-sensitive]** → Use serialized state transitions and race-focused tests for simultaneous subscriptions, final cancellation, callback cancellation, and late subscribers.
- **[Best-effort partial JSON may temporarily omit incomplete fields]** → Keep raw text private, never throw on partial parsing, and treat final available input as authoritative.
- **[Unknown tools and malformed unnamed calls have different failure scopes]** → Encode both paths explicitly and cover interleaved calls so one recoverable input error does not corrupt other state.

## Migration Plan

1. Introduce Java stream parts, callback contexts, and the internal provider SPI without routing production calls through it.
2. Implement OpenAI Chat Completions dialect fixtures and route OpenAI-compatible models through the SPI; keep generic models on final-only fallback.
3. Move tool normalization/validation ahead of approval and execution, then add callback sequencing and full-stream input lifecycle.
4. Replace shared `cache()` with the single-run replay coordinator and verify all existing projections.
5. Update UI Message mapping, Java/TypeScript delta types, npm partial parser, reducer, and consumer documentation together.
6. Run backend module tests, npm SDK tests/type checks, full Gradle tests, and protocol validation.

Rollback before release is a source revert of the coordinated Java/runtime/npm changes. No persisted extension data or provider configuration migration is required.

## Open Questions

None. Provider-specific dialects beyond standard OpenAI Chat Completions remain evidence-driven follow-up work rather than unresolved scope for this change.
