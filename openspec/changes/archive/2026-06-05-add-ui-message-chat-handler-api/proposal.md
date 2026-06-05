## Why

The UI message APIs now provide streaming, aggregation, validation, and conversion primitives, but consumer plugins still need to hand-wire the standard chat route flow. A framework-neutral Java chat handler can make the common backend path concise while preserving caller control over persistence, serialization, tools, and request options.

## What Changes

- Add a backend-only Java SDK helper for UI-message chat streaming.
- Provide a `UIMessageChatHandlers.streamText(...)` entry point that validates `UIMessage<M>` history, converts it to `ModelMessage`, invokes `LanguageModel.streamText(...)`, merges the model UI stream, and exposes a `UIMessageStreamResponse`.
- Add `UIMessageChatOptions<M>` for configuring `LanguageModel`, UI messages, optional continuation message, request builder customizer, validation/conversion options, serializer, metadata supplier, message id generator, finish callback, and error handling.
- Add `UIMessageChatResult<M>` exposing the UI stream, response descriptor, validation result, conversion result, and finish `Mono`.
- Fail fast on invalid UI messages, conversion failures, empty converted model messages, or request customizers that set `prompt`/`messages`.
- Keep conversion warnings observable but non-blocking by default.
- Document deferred follow-up work for dynamic message metadata callbacks, HTTP transport/request body schema for the future npm helper, and transport cancellation/abort mapping.

Non-goals:

- Do not add WebFlux/Halo endpoint adapters or HTTP request body parsing.
- Do not implement custom HTTP response headers/status/cookies.
- Do not implement automatic persistence.
- Do not add a safe handler variant.
- Do not add provider-aware reasoning state conversion.
- Do not add dynamic metadata lifecycle callbacks in this change.
- Do not add resumable streams, transport cancellation/abort mapping, or blocking APIs.
- Do not expose the underlying `StreamTextResult` from the handler.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: add a framework-neutral UI message chat handler that composes validation, conversion, model streaming, UI stream response creation, and finish aggregation.
- `consumer-sdk-documentation`: document the chat handler workflow and explicitly list deferred transport, metadata, and cancellation work.

## Impact

- Adds public Java SDK types under `api/src/main/java/run/halo/aifoundation/ui/`.
- Adds tests under `app/src/test/java/run/halo/aifoundation/ui/`.
- Updates consumer documentation in `dev/ui-message-stream.md` and documentation guardrails.
- Does not add dependencies to `api`; JSON serialization and HTTP response adaptation remain caller-provided.
