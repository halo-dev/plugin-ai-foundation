## Why

Consumer plugins can already call `LanguageModel.streamText(...)`, but turning the low-level model stream into a frontend-friendly interactive UI stream still requires each caller to invent its own protocol, SSE framing, lifecycle markers, custom data events, and error handling. This change introduces a Halo-owned Java API for server-to-frontend UI message streams, inspired by the architecture of AI SDK UI but not branded or specified as an AI SDK integration.

## What Changes

- Add a backend-only UI message stream API to the published `api` module.
- Introduce a Halo UI message chunk protocol for frontend-facing streams, separate from the existing `TextStreamPart` backend model stream protocol.
- Add `UIMessageStreamWriter` APIs for writing custom chunks, transient/non-transient data, text helpers, and merged model UI streams.
- Add conversion methods from `StreamTextResult` to UI message streams and response descriptors.
- Add `UIMessageStreamResponse` as an HTTP-friendly descriptor that carries SDK-defined headers and exposes both structured chunks and optionally encoded SSE body frames.
- Use Halo-owned protocol naming and headers, while keeping the protocol shape close to AI SDK UI concepts where that improves interoperability and developer familiarity.
- Document backend usage, WebFlux response wiring, SSE JSON chunk shape, and a minimal frontend parsing example.

### Non-Goals

- Do not add a frontend helper package or npm publication in this change.
- Do not implement full `readUIMessageStream` / `UIMessage` aggregation in this change.
- Do not bind the `api` module to Spring WebFlux response types.
- Do not claim this project is an AI SDK related project or expose Vercel/AI SDK branded protocol headers.
- Do not replace or deprecate `StreamTextResult.fullStream()` or `TextStreamPart`.

## Capabilities

### New Capabilities
- `ui-message-stream`: Frontend-facing UI message stream protocol and Java SDK helpers for writing, merging, converting, and responding with UI message chunks.

### Modified Capabilities
- None.

## Impact

- `api/src/main/java/run/halo/aifoundation/ui/`: new public Java SDK package.
- `api/src/main/java/run/halo/aifoundation/chat/StreamTextResult.java`: new convenience conversion methods.
- `dev/dev.md`: caller-facing examples and protocol documentation.
- API tests for chunk mapping, writer ordering, merge behavior, response headers, SSE body framing, and error handling.
- No generated console API client changes are expected because no console endpoint contract is changed.
