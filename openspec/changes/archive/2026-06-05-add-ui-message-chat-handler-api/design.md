## Context

The UI message stream API now has the lower-level pieces needed for a chat route:

- `UIMessageStream` and `UIMessageStreamResponse` for frontend-facing streaming
- `UIMessageStreams.createWithOptions(...)` for custom stream creation and finish aggregation
- `UIMessageValidators` for persisted UI message validation
- `UIMessageConverters` for converting UI messages back into `ModelMessage`
- `LanguageModel.streamText(...)` for provider-neutral model streaming

Consumer plugins can compose these pieces manually, but the route remains verbose and easy to wire incorrectly. The new handler is a framework-neutral Java SDK helper that standardizes this composition while keeping HTTP parsing, persistence, JSON serialization, and provider-specific behavior outside the helper.

## Goals / Non-Goals

**Goals:**

- Provide a pure `api` module chat helper for the common UI message backend route.
- Accept an already resolved `LanguageModel` and persisted `List<UIMessage<M>>`.
- Validate UI messages, convert them to `ModelMessage`, invoke `streamText`, merge the model UI stream, and expose a `UIMessageStreamResponse`.
- Return a full result object with validation result, conversion result, UI stream, response descriptor, and finish `Mono`.
- Preserve existing static metadata support through `metadataSupplier(...)`.
- Expose existing validation, conversion, finish, id generation, serializer, and error handling extension points.
- Reject request customizers that set `prompt` or `messages`; the handler owns model messages.

**Non-Goals:**

- No WebFlux/Halo endpoint adapter.
- No HTTP request body parser or frontend transport schema.
- No custom HTTP status/header/cookie support.
- No automatic persistence.
- No safe handler variant.
- No provider-aware reasoning converter.
- No dynamic message metadata lifecycle callback.
- No resumable stream, HTTP disconnect cancellation, abort mapping, or blocking API.
- No exposure of the underlying `StreamTextResult`.

## Decisions

### Handler lives in `api`

The helper only composes public SDK types and does not require app-layer provider resources, Spring beans, WebFlux, or Halo endpoint classes. It belongs beside the UI message SDK helpers under `api/src/main/java/run/halo/aifoundation/ui/`.

Alternative considered: place it in `app` to keep API lighter. Rejected because consumer plugins need compile-time access, and the helper remains pure SDK logic.

### Input is `LanguageModel`

`UIMessageChatOptions.model(LanguageModel)` is required. The caller remains responsible for resolving a default or named model through `AiModelService`.

Alternative considered: accept `AiModelService + modelName`. Rejected for the first version because model resolution is asynchronous and would expand the helper beyond the same shape as `streamText({ model, messages })`.

### Result object instead of response-only return

`UIMessageChatHandlers.streamText(...)` returns `UIMessageChatResult<M>`, not only `UIMessageStreamResponse`. The result exposes:

- `UIMessageStream stream()`
- `UIMessageStreamResponse response()`
- `UIMessageValidationResult<M> validation()`
- `UIMessageConversionResult conversion()`
- `Mono<UIMessageStreamFinish<M>> finish()`

This lets callers return the response while still observing conversion warnings and persisting finish messages.

### Request customizer owns non-input fields only

The handler exposes `request(Consumer<GenerateTextRequest.GenerateTextRequestBuilder>)`. The customizer can configure system, tools, provider options, output, timeouts, cancellation token, lifecycle callbacks, and other generation settings.

After applying the customizer, the handler builds a request and rejects it if `prompt` or `messages` were set. It then builds the final request with converted model messages. This avoids ambiguous input sources.

`baseRequest(GenerateTextRequest)` is deferred to avoid copy/merge ambiguity, especially for transient fields.

### Original messages feed finish aggregation

The same UI messages passed into the handler are used as `originalMessages` for `UIMessageStreams.createWithOptions(...)`. This gives finish aggregation the updated conversation list. Optional `message(existingAssistantMessage)` supports continuation.

### Finish handling uses one stream path

The handler must not create a second reader over the model stream. It delegates finish aggregation to `UIMessageStreams.createWithOptions(...)` and captures the finish callback with a `Mono`. If the user `onFinish` callback throws, `finish()` fails and the error is not silently swallowed.

### Serialization remains caller supplied

`serializer(Function<UIMessageChunk, String>)` is optional and is passed to `UIMessageStreamResponse`. The helper does not depend on a JSON library.

### Deferred follow-up work is explicit

This change must document, but not implement:

- dynamic message metadata callback and metadata merge lifecycle
- UI message HTTP transport/request body contract for the future npm helper
- transport cancellation and abort mapping

## Risks / Trade-offs

- [Risk] `GenerateTextRequest` has many fields, including transient callbacks. -> Mitigation: use the existing builder customizer and copy the built request through builder fields when applying converted messages.
- [Risk] `finish()` may not complete unless the stream is consumed. -> Mitigation: document that the result is reactive and finish depends on stream subscription.
- [Risk] Conversion warnings may hide skipped UI-only parts from simple callers. -> Mitigation: always expose `conversion()` on the result and document warning inspection.
- [Risk] Request customizers setting `prompt` or `messages` can break handler semantics. -> Mitigation: reject those inputs with `IllegalArgumentException`.
- [Risk] Metadata lifecycle needs richer semantics than `metadataSupplier`. -> Mitigation: explicitly defer dynamic metadata callback to a follow-up change.
