## Context

The SDK already exposes a Halo-owned UI message stream protocol, persistent `UIMessage` model, stream reader, validation/conversion helpers, and `UIMessageChatHandlers.streamText(...)`. That helper composes validation, conversion, model streaming, `UIMessageStreamResponse`, and finish aggregation, but callers still need to define their own HTTP request DTO for chat UI submissions.

AI SDK UI's `HttpChatTransport` uses a small default request body for chat submissions: chat id, UI messages, trigger, and optional message id. It treats resume as a separate reconnect flow and stop as request cancellation through `AbortSignal`, not as a POST trigger. The Halo Java API should mirror those concepts without importing frontend, Web, or WebFlux types into the `api` module.

## Goals / Non-Goals

**Goals:**

- Provide a framework-neutral Java request contract for chat submissions.
- Align the contract with AI SDK UI's default `HttpChatTransport` body shape.
- Support submit and regenerate triggers.
- Make regenerate behavior deterministic and testable.
- Keep response handling on the existing `UIMessageStreamResponse`.
- Document manual WebFlux glue code for the common Halo plugin use case.
- Preserve the separation between user-level regeneration and provider-level retry.
- Preserve the separation between stop/abort cancellation and chat triggers.

**Non-Goals:**

- No WebFlux adapter in this change.
- No app-module-only callable adapter.
- No new Spring WebFlux dependency in `api`.
- No resume stream endpoint, active stream registry, or replay store.
- No stop endpoint or stop trigger.
- No fixed extra body or request metadata protocol.
- No frontend npm helper.
- No compatibility layer for older unreleased request shapes.

## Decisions

### Keep the transport contract in `api`

Add pure Java types under `run.halo.aifoundation.ui`:

- `UIMessageChatRequest<M>`
- `UIMessageChatTrigger`

These types are safe for other Halo plugins to depend on through the published `api` module. They do not reference WebFlux, Servlet, Jackson, or Halo runtime classes.

Alternative considered: expose a WebFlux `ServerRequest`/`ServerResponse` adapter from `api`. This would make the common case shorter but would bind the public SDK to WebFlux and make non-WebFlux consumers pay for a dependency they may not need.

### Model only AI SDK's default send body first

`UIMessageChatRequest<M>` should contain:

- `id`
- `messages`
- `trigger`
- `messageId`

This matches the stable shape used by AI SDK's default HTTP transport for `sendMessages`. Extra `body` and request `metadata` are intentionally not fixed in the Java contract because AI SDK treats those as transport customization inputs and lets callers reshape them with `prepareSendMessagesRequest`.

Alternative considered: include `body` and `metadata` fields now. That would look flexible, but it would prematurely standardize fields that AI SDK leaves customizable and would complicate typed Java metadata semantics.

### Treat resume as a separate future contract

Do not add `RESUME_STREAM` to `UIMessageChatTrigger`. AI SDK implements resume through `reconnectToStream`, which is a separate transport method and defaults to a GET reconnect path. The Java SDK should document that this remains future work and avoid mixing reconnect semantics into POST submissions.

Alternative considered: add `RESUME_STREAM` as a third trigger. That would diverge from AI SDK's transport design and make the first request contract harder to reason about.

### Treat stop as cancellation, not a trigger

Do not add `STOP` to `UIMessageChatTrigger`. AI SDK's chat stop behavior aborts the active fetch request. In Java backend usage, callers should map HTTP/reactive cancellation to `GenerateTextRequest` cancellation support where needed.

Alternative considered: define a stop endpoint or stop trigger. That would require a server-side active stream registry, which belongs with resume support rather than the first framework-neutral request contract.

### Regenerate by trimming persisted UI history

`REGENERATE_MESSAGE` should require `messageId`, require the target message to exist, require it to be an assistant message, and call the model with the conversation history before that assistant message. The targeted assistant message and all following messages are excluded from validation/conversion/model invocation for this run.

This mirrors the user-facing meaning of regeneration: re-run the assistant response from the prior context. It is not provider retry. Provider retry remains controlled by `GenerateTextRequest.maxRetries`.

Alternative considered: keep all messages and ask the model to overwrite a message. That would leak the old assistant answer into the new generation context and produce surprising results.

### Reuse `UIMessageChatHandlers.streamText(...)`

Add an overload or option path that accepts `UIMessageChatRequest<M>` and resolves the effective message list before the existing validation/conversion/model flow. The resulting `UIMessageChatResult<M>` should still expose the same stream, response, validation, conversion, and finish projections.

The handler should use the effective messages as `originalMessages` for finish aggregation, so regeneration replaces the trimmed history with the newly generated assistant response rather than appending after the old response.

## Risks / Trade-offs

- [Risk] Callers still write a few WebFlux lines to parse JSON and return SSE. → Mitigation: document a concise copyable WebFlux example using `bodyToMono`, `UIMessageChatHandlers.streamText`, and `UIMessageStreamResponse`.
- [Risk] Regeneration semantics can surprise callers if they expect provider retry. → Mitigation: document the distinction and add tests that `maxRetries` remains only a request customization field.
- [Risk] Excluding extra body and metadata can feel too small for advanced callers. → Mitigation: document that callers can wrap `UIMessageChatRequest<M>` in their own endpoint DTO and feed custom fields into request customizers or persistence logic.
- [Risk] Stop/abort behavior remains manual. → Mitigation: document cancellation as deferred helper work and keep `GenerateTextRequest` cancellation integration available through the existing request customizer.
- [Risk] Resume will need storage decisions later. → Mitigation: explicitly record resume endpoint, active stream registry, and replay strategy as deferred work aligned with AI SDK `reconnectToStream`.
