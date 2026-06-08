## Context

`LanguageModel.streamText(...)` currently exposes `StreamTextResult`, whose `fullStream()` is a low-level backend model stream made of `TextStreamPart` events. That stream is appropriate for SDK consumers that need provider-neutral model lifecycle, reasoning, tool, source, file, finish, and error parts.

Consumer plugins also need a higher-level server-to-frontend stream that can be returned over HTTP, merged with model output, and augmented with plugin-owned UI data such as retrieval status, sources, business cards, or progress markers. Each plugin should not invent its own SSE protocol or manually remember response headers.

This design introduces a Halo-owned UI message stream API in the `api` module. It borrows the architecture shape of AI SDK UI but remains Halo branded and independent.

## Goals / Non-Goals

**Goals:**
- Add a public Java SDK API for frontend-facing UI message streams.
- Keep `TextStreamPart/fullStream()` and `UIMessageStream` as separate protocols with separate use cases.
- Let callers write typed chunks, custom data, transient data, text helpers, and merged model UI streams.
- Let `StreamTextResult` convert to `UIMessageStream` and an HTTP-friendly `UIMessageStreamResponse`.
- Carry SDK-defined protocol headers automatically in the response descriptor.
- Support SSE JSON chunk framing without forcing Spring WebFlux or Jackson into the `api` module.
- Keep the first implementation backend-only and documented for consumer plugins.

**Non-Goals:**
- No npm frontend helper package in this change.
- No full `readUIMessageStream` / UI message aggregation in this change.
- No Spring `ServerResponse`, `ServerSentEvent`, or WebFlux dependency in the `api` module.
- No hard commitment to AI SDK wire compatibility or AI SDK branded headers.
- No replacement or deprecation of `TextStreamPart`.

## Decisions

### Use a Halo-owned protocol and header

The response descriptor will include a Halo protocol header such as `X-Halo-AI-UI-Message-Stream: v1`.

Alternatives considered:
- Use an AI SDK/Vercel header. Rejected because this project is not an AI SDK related project.
- Require callers to add the header manually. Rejected because the SDK should own protocol markers.

### Keep HTTP framework integration outside `api`

`UIMessageStreamResponse` will carry headers and body streams, but will not expose Spring WebFlux types. Consumers can adapt it to their own HTTP stack:

```java
var response = result.toUIMessageStreamResponse(serializer);

return ServerResponse.ok()
    .headers(headers -> headers.setAll(response.headers()))
    .contentType(MediaType.TEXT_EVENT_STREAM)
    .body(response.body(), String.class);
```

Alternatives considered:
- Add WebFlux helpers directly to `api`. Rejected to keep the published SDK light and framework-neutral.
- Put the whole feature in `app`. Rejected because consumer plugins need the API directly.

### Use class hierarchy with records

`UIMessageChunk` will be a sealed interface, with each chunk type represented by its own `record` in `run.halo.aifoundation.ui`. Each record will expose a `type()` discriminator and explicit constructor parameters.

Alternatives considered:
- One DTO with nullable fields and a `type` discriminator. Rejected because typed records make Java caller code clearer and prevent many invalid chunk shapes.
- Nested chunk classes inside one file. Rejected because separate public files are easier for SDK users and documentation.

### Provide static factories and writer helpers

The API will include `UIMessageChunks` factory methods and writer helpers such as:

```java
writer.write(UIMessageChunks.data("sources", sources));
writer.writeData("status", "retrieving", true);
writer.writeText("Hello, world!");
writer.merge(result.toUIMessageStream());
```

The low-level `writer.write(UIMessageChunk)` remains available.

### Preserve ordering and avoid lifecycle magic

`UIMessageStreamWriter` will preserve write and merge order, propagate errors through configured error handling, and perform cleanup when merged streams complete. It will not reorder chunks or infer business semantics across messages.

`StreamTextResult.toUIMessageStream()` is responsible for producing consistent block IDs from the underlying `TextStreamPart` stream. Callers who manually write start/delta/end chunks are responsible for matching IDs, with helper methods available to reduce mistakes.

### Support transient data

`DataChunk` will use `transientData` to distinguish ephemeral UI state from data that can be persisted with a message. The default for simple `writeData` calls is non-transient.

### Expose structured and encoded response body views

`UIMessageStreamResponse` will expose:
- `headers()`: protocol headers owned by the SDK.
- `stream()`: `Flux<UIMessageChunk>` for structured consumption.
- `body()`: `Flux<String>` SSE frames when a serializer is supplied.

The API will not require Jackson. Callers supply a serializer such as `chunk -> objectMapper.writeValueAsString(chunk)` when they want encoded SSE body frames.

### Convert low-level model parts to UI chunks

Mapping from `TextStreamPart` to UI chunks will include:
- `start`
- `text-start`, `text-delta`, `text-end`
- `reasoning-start`, `reasoning-delta`, `reasoning-end`
- `data`
- `source-url`
- `file`
- `tool-input-start`, `tool-input-delta`
- `tool-call`, `tool-result`, `tool-error`
- `tool-approval-request`
- `finish`
- `error`
- `abort`

`raw` diagnostic events are not mapped by default. Callers needing low-level diagnostic metadata can continue to use `fullStream()`.

### Keep finish aggregation light

The first version will not build complete UI messages. It may expose lightweight finish metadata such as message id, finish reason, usage, aborted state, and error text through callbacks or summary types.

Full `readUIMessageStream` behavior is left for a future frontend helper or second-stage Java helper.

## Risks / Trade-offs

- [Risk] More public Java types than a single DTO design. -> Mitigation: keep them in one package, provide `UIMessageChunks` factories, and document the common path.
- [Risk] Consumers may expect AI SDK wire compatibility. -> Mitigation: use Halo-owned headers and documentation language, while describing only protocol similarities.
- [Risk] Without built-in Jackson, direct SSE body setup is one extra argument. -> Mitigation: provide response descriptors and examples using the caller's existing `ObjectMapper`.
- [Risk] Manual chunk writing can create mismatched start/delta/end IDs. -> Mitigation: provide `writeText(...)` and clear docs; model conversion handles IDs automatically.
- [Risk] Error handling could hide server bugs. -> Mitigation: only convert stream-generation errors to `ErrorChunk`; serializer failures still fail the response body.

## Migration Plan

This is a new additive API in an unreleased plugin. No migration is required.

Implementation can proceed in three phases:
1. Add API types, writer, response descriptor, and tests in `api`.
2. Add `StreamTextResult` conversion methods and mapping tests.
3. Update `dev/dev.md` with backend usage and minimal frontend parsing examples.

Rollback is deleting the new `ui` package, removing `StreamTextResult` conversion methods, and reverting documentation updates.

## Open Questions

- The exact JSON field shape for polymorphic records depends on the caller's serializer configuration. The implementation should document a tested Jackson setup without making Jackson an `api` dependency.
- Future frontend helper package scope and API are intentionally deferred until the backend protocol is implemented.
