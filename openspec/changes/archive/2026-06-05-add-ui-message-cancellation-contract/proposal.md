## Why

UI message chat streams can currently be consumed and disposed by caller code, but the Java SDK does not define how transport cancellation maps into model generation cancellation, UI abort chunks, or finish aggregation. This leaves plugin authors to invent their own stop behavior and risks treating user cancellation as an error.

This backend-only change adds a framework-neutral cancellation contract for UI message chat handling, so WebFlux and other transports can wire disconnects or stop actions into the existing generation cancellation model without adding a framework adapter.

## What Changes

- Add a caller-owned UI message cancellation helper that exposes a `CancellationToken` and Reactor cancel binding helpers.
- Add `UIMessageChatOptions.cancellationToken(...)` so the chat handler can inject cancellation into the underlying `GenerateTextRequest`.
- Reject request customizers that set `cancellationToken`, matching the existing handler ownership of `prompt` and `messages`.
- Map recognized generation cancellation to an `abort` UI chunk instead of an `error` chunk.
- Preserve finish aggregation on cancellation so `onFinish` still receives partial response messages with `aborted = true`.
- Enforce a single terminal UI stream chunk for SDK-created streams: `finish`, `error`, or `abort`.
- Document manual transport usage, including WebFlux-style subscriber cancellation, without introducing a WebFlux adapter.
- Record deferred boundaries for stop endpoints, resume/reconnect, active stream registry, cancellation reasons, and npm helper behavior.

## Non-goals

- Do not add a WebFlux, Servlet, or Halo endpoint adapter.
- Do not add a stop HTTP endpoint.
- Do not add active stream registry, resume, reconnect, replay, or stream id behavior.
- Do not add cancellation reasons.
- Do not add npm helper behavior.
- Do not change low-level `StreamTextResult.fullStream()` semantics.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Define the framework-neutral UI message cancellation contract, handler token ownership, abort mapping, finish behavior, and terminal chunk invariants.
- `consumer-sdk-documentation`: Document how plugin authors wire cancellation manually and which cancellation-related features remain out of scope.

## Impact

- Public API module:
  - `run.halo.aifoundation.ui.UIMessageChatOptions`
  - UI message stream creation/writer error handling
  - New framework-neutral UI cancellation helper types
- Tests for chat handler request construction, cancellation-to-abort behavior, finish aggregation, and helper cancel binding.
- Consumer documentation in `dev/ui-message-stream.md`.
- OpenSpec specs for `ui-message-stream` and `consumer-sdk-documentation`.
