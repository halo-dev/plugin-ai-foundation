## Why

The current UI message chat helper can stream from persisted `UIMessage` values, but callers still lack a documented request contract that aligns with AI SDK UI's `HttpChatTransport` default body shape. Defining that backend Java contract now gives plugin authors a stable way to accept chat requests and return `UIMessageStreamResponse` while keeping the SDK framework-neutral.

This is a backend-only change. It intentionally avoids a WebFlux adapter and future npm helper work until the Java-side contract is stable.

## What Changes

- Add a framework-neutral `UIMessageChatRequest<M>` request model for AI SDK-style chat submissions.
- Add `UIMessageChatTrigger` with `SUBMIT_MESSAGE` and `REGENERATE_MESSAGE`.
- Add handler support for starting chat streams from `UIMessageChatRequest<M>`.
- Define `REGENERATE_MESSAGE` semantics as user-driven response regeneration:
  - `messageId` is required.
  - `messageId` must target an existing assistant `UIMessage`.
  - The targeted assistant message and all later messages are removed before model invocation.
  - The regenerated stream uses the remaining message history.
- Document that regeneration is separate from provider retry settings such as `maxRetries`.
- Document that stop/abort is not a chat trigger; it is handled through HTTP/reactive cancellation and caller-provided cancellation tokens.
- Document that resume stream remains deferred and should follow AI SDK's separate reconnect path later.
- Document manual WebFlux glue code for request parsing and response writing without adding WebFlux types to the `api` module.

Non-goals:

- No WebFlux adapter.
- No app-module-only callable adapter.
- No resume endpoint or active stream registry.
- No stop endpoint.
- No fixed extra body or request metadata protocol.
- No frontend npm helper.
- No change to the existing low-level `fullStream()` protocol.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Define the framework-neutral UI message chat transport request contract and regeneration behavior.
- `consumer-sdk-documentation`: Document how plugin authors parse request DTOs, call the chat handler, return `UIMessageStreamResponse`, and handle deferred stop/resume/WebFlux adapter concerns.

## Impact

- Public Java API in `api/src/main/java/run/halo/aifoundation/ui/`.
- UI message chat handler tests in `app/src/test/java/run/halo/aifoundation/ui/`.
- Consumer documentation in `dev/ui-message-stream.md`.
- OpenSpec requirements for `ui-message-stream` and `consumer-sdk-documentation`.
- No new runtime dependency and no Spring WebFlux dependency in the `api` module.
