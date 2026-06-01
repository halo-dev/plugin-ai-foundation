## Why

The text-generation API now covers the main generate/stream/tool/structured-output surface, but callers still lack first-class controls for observing lifecycle events, cancelling long-running work, and enforcing timeouts. provider-neutral AI API treats these as per-call primitives, so adding them now will make the already implemented text-generation path more usable and easier to operate in real plugins.

## What Changes

- Add provider-neutral lifecycle callback contracts for generation start, step start, tool execution start/finish, step finish, generation finish, and generation error.
- Add request-scoped cancellation and timeout controls that work for `generateText`, `streamText`, `embed`, and `embedMany` where applicable.
- Add metadata/context fields that flow through lifecycle callbacks without becoming provider-specific public API.
- Ensure callbacks observe the actual execution path without triggering duplicate provider calls or changing stream ordering.
- Document SDK-level stopping semantics: stop conditions, provider stop sequences, timeout, and cancellation.
- Non-goals: no middleware framework, no telemetry exporter, no DevTools UI, no provider registry changes, no MCP integration, and no image/video/speech/transcription work in this change.

## Capabilities

### New Capabilities

- `generation-lifecycle-controls`: Lifecycle callbacks, cancellation, timeout, and call metadata for provider-neutral AI generation APIs.

### Modified Capabilities

- `ai-model-service`: Request/result contracts gain lifecycle callback, cancellation, timeout, and metadata behavior.
- `step-control`: Step control integrates with lifecycle callbacks and timeout/cancellation decisions.
- `stream-text-result`: Stream projections observe cancellation and error semantics consistently.
- `streaming-tool-calls`: Tool execution emits lifecycle events and respects cancellation/timeout.

## Impact

- `api/`: New provider-neutral callback/event/control types and new fields on request DTOs.
- `app/`: `LanguageModelImpl` and embedding implementation need callback invocation, timeout enforcement, cancellation checks, and error propagation.
- `ui/`: No broad UI feature is planned; generated client may change if serializable request fields change, but Java callback/cancellation fields must stay out of OpenAPI.
- `dev/dev.md`: Update SDK examples for callbacks, timeout, cancellation, and stop semantics.
- Tests: Backend tests for callback order, timeout, cancellation, error propagation, and no duplicate provider/tool execution; frontend generated-client checks if schemas change.
