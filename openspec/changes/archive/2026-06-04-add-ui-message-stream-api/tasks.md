## 1. Public API Types

- [x] 1.1 Create `run.halo.aifoundation.ui.UIMessageChunk` as a sealed interface with a stable `type()` discriminator.
- [x] 1.2 Add `UIMessageChunkType` constants for all supported chunk types.
- [x] 1.3 Add independent record files for start, text, reasoning, data, source, file, tool, finish, error, and abort chunks.
- [x] 1.4 Add `UIMessageChunks` static factory methods for common chunk creation, including data and transient data.

## 2. Stream Writer

- [x] 2.1 Add `UIMessageStream` as a structured `Flux<UIMessageChunk>` wrapper.
- [x] 2.2 Add `UIMessageStreamWriter` with `write`, `merge`, `writeData`, `writeText`, and transient data helpers.
- [x] 2.3 Implement `UIMessageStreams.create(...)` with ordered write/merge behavior and default id generation.
- [x] 2.4 Add configurable stream error handling that converts merged stream failures to `ErrorChunk` with default `"An error occurred."` text.
- [x] 2.5 Add lightweight finish callback support without full UI message aggregation.

## 3. StreamTextResult Conversion

- [x] 3.1 Add `StreamTextResult.toUIMessageStream()` mapping low-level text, reasoning, source, file, tool, finish, error, and abort parts to UI chunks.
- [x] 3.2 Ensure raw diagnostic parts are not emitted by default in UI message streams.
- [x] 3.3 Add `StreamTextResult.toUIMessageStreamResponse(...)` convenience methods.

## 4. Response Descriptor and SSE Encoding

- [x] 4.1 Add `UIMessageStreamResponse` with `headers()`, `stream()`, and serializer-backed `body()` views.
- [x] 4.2 Ensure response headers include the Halo UI message stream protocol marker and version.
- [x] 4.3 Encode serialized chunks as SSE `data:` frames and append `data: [DONE]` on normal completion.
- [x] 4.4 Keep JSON serialization caller-supplied so the `api` module does not add a Jackson dependency.

## 5. Tests

- [x] 5.1 Add unit tests for chunk factories and type discriminator values.
- [x] 5.2 Add unit tests for writer ordering, merge completion, writeData transient semantics, and writeText id consistency.
- [x] 5.3 Add unit tests for `StreamTextResult` to UI chunk mapping across text, reasoning, source, file, tool, finish, error, and abort events.
- [x] 5.4 Add unit tests for raw diagnostic exclusion.
- [x] 5.5 Add unit tests for response headers, SSE framing, `[DONE]`, serializer-supplied encoding, and serializer failure behavior.
- [x] 5.6 Add unit tests for default and custom error handling.

## 6. Documentation

- [x] 6.1 Update `dev/dev.md` with the distinction between `fullStream()` and UI message streams.
- [x] 6.2 Document custom data, transient data, writeText, merge, response headers, and WebFlux response adaptation.
- [x] 6.3 Add a minimal frontend EventSource parsing example.
- [x] 6.4 Document that frontend helper/npm package work is deferred to a future change.

## 7. Validation

- [x] 7.1 Run focused API tests for the new UI message stream package.
- [x] 7.2 Run `./gradlew :api:compileJava`.
- [x] 7.3 Run `./gradlew test` or the focused affected test classes if the full suite is too slow.
- [x] 7.4 Run `git diff --check`.
