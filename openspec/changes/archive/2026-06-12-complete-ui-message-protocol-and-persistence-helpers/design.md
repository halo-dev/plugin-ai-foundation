## Context

The Java SDK already exposes full-stream parts such as `start-step`, `tool-input-start`, `tool-input-delta`, `tool-call`, `tool-result`, `tool-error`, and approval events. The current UI message mapper collapses most tool events into dynamic `tool-<name>` chunks, while the npm reducer also uses dynamic `tool-<name>` as the final message part shape. This works for the current workbench, but it blurs the boundary between streamed transport events and accumulated UI state.

The Vue package also has real chat, data, tool, schema, and stream-reader paths, but callers still lack package-level helpers for pruning and validating persisted UI messages. High-frequency streams currently commit each accepted chunk directly to Vue state, which can cause unnecessary render churn for long responses.

## Goals / Non-Goals

**Goals:**

- Make the external UI message stream protocol use canonical lifecycle chunks for step and tool events.
- Preserve final dynamic `tool-<name>` message parts as the UI state model used by callers and the workbench.
- Add lightweight npm helpers for validating and pruning persisted UI messages.
- Add `experimental_throttle` to reduce reactive state commit frequency without changing stream consumption or tool callback semantics.
- Keep Java and npm protocol types, reducer behavior, tests, and docs aligned.

**Non-Goals:**

- No stream resume or reconnect support.
- No `source-document` support until the backend has a real document source model.
- No file model expansion or upload management.
- No npm-side model-message conversion that duplicates Java server-side conversion.
- No token-based pruning or tokenizer dependency.

## Decisions

### Canonical tool chunks remain separate from final tool parts

Transport chunks SHALL use canonical event names such as `tool-input-start`, `tool-input-delta`, `tool-input-available`, `tool-output-available`, `tool-output-error`, `tool-approval-request`, and `tool-approval-response`. The reducer SHALL merge those events into final dynamic `tool-<name>` parts.

Alternative considered: keep dynamic `tool-<name>` as the external stream chunk. This is simpler but makes the wire protocol part-like instead of event-like and hides the distinction between streamed deltas and accumulated state.

### `start-step` is lifecycle-only

`start-step` SHALL be emitted and parsed as a lifecycle chunk, but SHALL NOT be appended to `message.parts`. Terminal step details remain represented by `finish-step` terminal state, not by a visible part.

Alternative considered: store step lifecycle chunks in message parts. That would expose internal provider-step boundaries as renderable UI state and make persisted messages noisier.

### `source-document` is deferred

The package SHALL NOT add a visible `source-document` part in this change because the backend does not yet have document-source semantics. `source-url` and `file` remain supported as current real capabilities.

Alternative considered: add frontend-only `source-document` types now. That would create a protocol placeholder that no backend path can produce.

### Persistence helpers stay UI-message scoped

`pruneMessages` and `validateUIMessages` SHALL operate on UI message structure and persisted part states only. Java `UIMessageConverters` remains the server-side model-message conversion authority because conversion depends on backend tool definitions, approval state, and model context.

Alternative considered: implement `convertToModelMessages` in npm. That risks a partial converter whose semantics drift from the Java SDK.

### Throttling applies only to Vue-visible commits

`experimental_throttle` SHALL throttle only `messages` state commits that notify Vue consumers. Stream reading, reducer application, `onData`, `onToolCall`, errors, aborts, and terminal flushes SHALL remain immediate.

Alternative considered: throttle chunk consumption or reducer application. That would delay tool callbacks and may introduce transport buffering or backpressure surprises.

## Risks / Trade-offs

- Canonical tool chunks require touching Java records, transport codec, mapper, TypeScript types, reducer, and tests -> Mitigation: implement end-to-end tests that assert Java mapper output and npm reducer final parts.
- Legacy dynamic `tool-*` chunks remain readable briefly -> Mitigation: mark them as internal-compatible, stop documenting them as the external protocol, and ensure Java no longer emits them.
- `validateUIMessages` is intentionally lighter than Java conversion validation -> Mitigation: document that it validates persistence shape, not model-conversion readiness.
- Throttle can hide intermediate render frames from Vue consumers -> Mitigation: keep callbacks immediate and force flush on terminal, stop, reset, error, and explicit `setMessages`.
