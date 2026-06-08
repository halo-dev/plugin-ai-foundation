## Why

The UI message model already has typed message metadata, but the stream protocol only sets metadata once through `metadataSupplier`. To align more closely with AI SDK UI and support real chat state updates, the Java backend needs a message metadata lifecycle that can update metadata at stream start, during streaming, and at finish.

This is a backend-only change focused on the Java UI message stream protocol and aggregation behavior.

## What Changes

- Add message-level metadata support to UI message stream chunks:
  - `StartChunk.messageMetadata`
  - `FinishChunk.messageMetadata`
  - new `MessageMetadataChunk` with type `message-metadata`
- Add factories and writer helpers:
  - `UIMessageChunks.start(messageId, messageMetadata)`
  - `UIMessageChunks.finish(finishReason, rawFinishReason, usage, messageMetadata)`
  - `UIMessageChunks.messageMetadata(messageMetadata)`
  - `UIMessageStreamWriter.writeMessageMetadata(messageMetadata)`
- Add metadata aggregation in `UIMessageStreamReader`:
  - existing assistant message metadata is the initial value when provided
  - otherwise `metadataSupplier` provides the initial value
  - start, message-metadata, and finish metadata merge in stream order
  - metadata changes emit message snapshots
  - metadata chunks do not enter `UIMessage.parts`
- Add `UIMessageMetadataMerger<M>` and configurable `metadataMerger(...)` on stream reader and stream creation options.
- Define default merge behavior:
  - `null` update is a no-op
  - `Map<String, Object>` metadata uses shallow merge with later keys overriding earlier keys
  - non-Map metadata replaces the current metadata
- Document message metadata lifecycle and how it differs from `DataPart` and transient data.

Non-goals:

- No `UIMessageChatOptions` metadata lifecycle shortcut in this change.
- No automatic mapping from `StreamTextResult` request/response metadata, usage, model id, or finish reason into message metadata.
- No `finish.metadata()` or terminal metadata field; metadata remains accessible through `finish.responseMessage().metadata()`.
- No frontend npm helper.
- No WebFlux adapter.
- No resume stream behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Add message metadata chunks, metadata aggregation, merge behavior, and writer/reader options.
- `consumer-sdk-documentation`: Document message metadata lifecycle and its boundaries relative to data parts, transient data, chat handlers, and future frontend helpers.

## Impact

- Public Java API in `api/src/main/java/run/halo/aifoundation/ui/`.
- UI message stream reader/writer behavior and tests.
- UI message stream documentation in `dev/ui-message-stream.md`.
- OpenSpec requirements for `ui-message-stream` and `consumer-sdk-documentation`.
- No new runtime dependencies and no Spring/WebFlux dependency in the `api` module.
