## Why

AI Foundation already reserves `tool-input-start` and `tool-input-delta` in its full-stream and UI Message protocols, but the backend currently receives completed tool calls from provider adapters and therefore cannot produce real incremental tool input. This leaves consumers unable to render progressive tool arguments or observe tool-input lifecycle events with the behavior available in AI SDK v6.

## What Changes

- Preserve provider-native tool argument fragments through an internal streaming adapter contract and emit real `tool-input-start`, `tool-input-delta`, and `tool-input-end` full-stream parts without fabricating deltas for final-only providers.
- Add provider-neutral, backpressured `ToolDefinition` input lifecycle callbacks for input start, delta, and final availability, with immutable context, cancellation, timeout, validation, and repair semantics.
- Normalize and validate completed tool input for all known tools, including externally executed tools, before approval or execution; distinguish recoverable unknown-tool calls from malformed provider streams that never identify a tool.
- Project incremental tool input into the UI Message stream and incrementally parse partial JSON in the npm SDK while keeping incomplete raw JSON private to the reducer.
- Replace the current unbounded Reactor cache with a cancellable single-run replay coordinator so all `StreamTextResult` projections share one execution while last-subscriber cancellation reaches provider and callback work.
- Add an internal OpenAI-compatible stream dialect boundary and fixture coverage so providers can share the standard Chat Completions parser while provider-specific behavior remains replaceable when evidence requires it.
- **BREAKING** Remove `toolName` from the Java and TypeScript `tool-input-delta` chunk shape; reducers and consumers resolve the name from the preceding `tool-input-start` state.

## Non-goals

- Do not synthesize incremental deltas when a provider exposes only completed tool arguments.
- Do not add a static provider capability table, negative capability cache, or live-provider calls to CI; support is determined from each observed response stream and verified with fixtures.
- Do not add a custom progressive tool-input protocol for Ollama unless its native API later provides a reliable contract.
- Do not migrate providers to OpenAI Responses API or redesign the console provider-management UI.

This is a cross-layer change affecting the backend runtime, public Java SDK, UI Message wire contract, and npm SDK reducer. It is not backend-only or UI-only.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `streaming-tool-calls`: Preserve provider-native tool-input fragments and define callback, validation, repair, provider compatibility, and error-boundary behavior.
- `stream-text-result`: Expose the complete tool-input lifecycle and provide cancellable single-execution replay across all result projections.
- `ui-message-stream`: Carry canonical incremental tool-input chunks and maintain partially parsed tool input in the npm reducer.
- `structured-tool-io`: Extend provider-neutral tool definitions and contexts with input lifecycle callbacks and require validation for internal and external tools.
- `stream-protocol-invariants`: Define exact full-stream and UI projection ordering for incremental tool input.

## Impact

- Public Java API types under `api`: `ToolDefinition`, new tool-input callback contexts, `ToolExecutionContext`, `TextStreamPart`, and `ToolInputDeltaChunk`.
- Backend streaming orchestration under `app`: provider stream adapters and dialects, tool-call accumulation, validation/repair, callback sequencing, and shared stream replay.
- OpenAI-compatible providers gain native delta support when their actual SSE stream carries incremental arguments; generic and Ollama-backed models retain final-only behavior.
- npm SDK types and UI Message reducer change to the canonical delta wire shape and AI SDK v6-style partial JSON accumulation.
- Backend, protocol, and npm SDK tests gain deterministic provider fixtures, lifecycle ordering, cancellation/replay, and reducer coverage.
- No new runtime dependency is required for partial JSON parsing; the required repair/parser behavior is implemented in the npm SDK.
