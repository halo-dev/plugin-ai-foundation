## Context

The UI message backend APIs now provide framework-neutral chat handling, transport request models, stream responses, message aggregation, metadata lifecycle updates, and conversion helpers. Callers can manually write WebFlux or other endpoint glue by parsing a `UIMessageChatRequest`, invoking `UIMessageChatHandlers.streamText(...)`, and returning `UIMessageStreamResponse` headers and body frames.

The missing lifecycle contract is cancellation. The lower-level generation request already exposes `CancellationToken`, and generation/tool runtime code can observe cancellation. The UI chat handler does not yet own a cancellation option, does not prevent request customizers from setting lifecycle fields inconsistently, and does not define how cancellation becomes UI protocol output.

This change stays in the public API module and keeps transport integration framework-neutral. WebFlux remains an example of how callers wire subscriber cancellation into the SDK; it is not an adapter dependency.

## Goals / Non-Goals

**Goals:**

- Let callers pass a cancellation token through `UIMessageChatOptions`.
- Preserve handler ownership of chat input and transport lifecycle by rejecting request customizer `cancellationToken` overrides.
- Provide a small UI message cancellation helper around the existing cancellation token/source concept.
- Provide Reactor `Flux` and `Mono` subscriber-cancel binding helpers because the API module already exposes Reactor stream views.
- Convert recognized generation cancellation to an `abort` chunk rather than an `error` chunk.
- Preserve partial response aggregation and invoke `onFinish` with `aborted = true`.
- Ensure SDK-created UI streams emit at most one terminal chunk: `finish`, `error`, or `abort`.
- Document manual cancellation wiring and deferred transport features.

**Non-Goals:**

- No WebFlux, Servlet, or Halo runtime adapter.
- No stop endpoint, active stream registry, resume, reconnect, replay, or stream id contract.
- No cancellation reason model.
- No npm helper behavior.
- No change to `StreamTextResult.fullStream()` semantics.

## Decisions

### Handler owns cancellation token injection

`UIMessageChatOptions<M>` will expose `cancellationToken(CancellationToken token)`. `UIMessageChatHandlers` will inject that token into the final `GenerateTextRequest` after validation and UI-to-model conversion.

The request customizer will be rejected if it sets `cancellationToken`, just as it is rejected for `prompt` and `messages`. This keeps chat input and transport lifecycle under the chat handler contract, while leaving generation settings, tools, provider options, lifecycle callbacks, and timeouts configurable.

Alternative considered: let request customizers set cancellation. This is more flexible but makes transport cancellation easy to bypass and creates two competing ownership paths.

### Cancellation helper remains framework-neutral

Add a small `UIMessageCancellation` helper, created through `UIMessageCancellations.create()`, that exposes:

- `token()`
- `cancel()`
- `isCancellationRequested()`
- `cancelWhenSubscriberCancels(Flux<T>)`
- `cancelWhenSubscriberCancels(Mono<T>)`

The Reactor helpers only call `cancel()` when Reactor reports `SignalType.CANCEL`. They must not cancel on normal completion or error.

Alternative considered: provide WebFlux-specific helpers. That would make common usage easier but would put transport types into the public API module and conflict with the current framework-neutral contract.

### Cancellation maps to abort

Recognized generation cancellation, including `AiGenerationCancelledException` and streams ending while the configured token is already cancelled, will be mapped to `UIMessageChunks.abort()`.

Cancellation must not call the normal safe error handler, must not write an `error` chunk, and must not make `UIMessageStreamResponse.body()` fail as its primary protocol behavior. Non-cancellation exceptions keep existing error handling behavior.

Alternative considered: let cancellation fail the response body. That leaks an expected user lifecycle event as an error and makes frontend state harder to distinguish.

### Finish aggregation survives cancellation

The UI message reader already treats abort as terminal state. SDK-created streams will still complete finish aggregation after abort, so callers can inspect:

- partial `responseMessage()`
- `updatedMessages()`
- `aborted() == true`
- no error text for expected cancellation

`onFinish` remains the single terminal callback. No `onAbort` callback is introduced in this version.

### Single terminal chunk invariant

Writer/stream creation behavior will guard against emitting multiple terminal chunks. For SDK-created streams, the first terminal state wins:

- `finish`
- `error`
- `abort`

After any terminal chunk is emitted, later terminal attempts are ignored. The reader can still handle malformed external streams defensively, but SDK-generated output should be protocol-clean.

## Risks / Trade-offs

- [Risk] Some providers may not observe `CancellationToken` immediately. -> The Reactor subscriber-cancel path still cancels the subscription, and token propagation gives tools and lifecycle code a cooperative cancellation signal.
- [Risk] Mapping cancellation to abort could hide real failures if detection is too broad. -> Only recognized cancellation exceptions or already-cancelled configured tokens should map to abort; ordinary provider/runtime failures remain errors.
- [Risk] Single terminal guarding could suppress useful late diagnostics. -> Terminal chunks are protocol state, not diagnostics. Late diagnostics can remain logs or lifecycle callbacks in future work.
- [Risk] Callers may expect a stop endpoint. -> Documentation will explicitly state that stop endpoints require active stream registry work and remain deferred.

## Migration Plan

This plugin is unreleased, so no compatibility migration is required. Existing callers that do not configure cancellation keep current behavior. Callers that need transport cancellation can opt in by creating `UIMessageCancellation`, passing its token to chat options, and wrapping response body streams with `cancelWhenSubscriberCancels(...)`.

Rollback is straightforward: remove the new helper/types and option wiring before release if the contract proves too broad.

## Open Questions

None. Stop endpoint, resume/reconnect, active stream registry, cancellation reasons, and npm helper behavior are intentionally deferred to later changes.
