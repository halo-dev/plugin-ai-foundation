## 1. Chunk Protocol API

- [x] 1.1 Add `UIMessageChunkType.MESSAGE_METADATA`.
- [x] 1.2 Add `MessageMetadataChunk` with `messageMetadata` and type `message-metadata`.
- [x] 1.3 Add `MessageMetadataChunk` to the sealed `UIMessageChunk` hierarchy.
- [x] 1.4 Add optional `messageMetadata` to `StartChunk` while preserving the existing start constructor and factory.
- [x] 1.5 Add optional `messageMetadata` to `FinishChunk` while preserving the existing finish constructor and factory.
- [x] 1.6 Add `UIMessageChunks.start(messageId, messageMetadata)`.
- [x] 1.7 Add `UIMessageChunks.finish(finishReason, rawFinishReason, usage, messageMetadata)`.
- [x] 1.8 Add `UIMessageChunks.messageMetadata(messageMetadata)`.
- [x] 1.9 Add `UIMessageStreamWriter.writeMessageMetadata(messageMetadata)`.

## 2. Metadata Merge Options

- [x] 2.1 Add `UIMessageMetadataMerger<M>` functional interface.
- [x] 2.2 Implement default merge behavior: null update no-op, Map shallow merge, non-Map replace.
- [x] 2.3 Add `metadataMerger(...)` to `UIMessageStreamReaderOptions<M>`.
- [x] 2.4 Add `metadataMerger(...)` to `UIMessageStreamOptions<M>`.
- [x] 2.5 Pass stream creation metadata merger into the internal reader used for finish aggregation.

## 3. Reader Aggregation

- [x] 3.1 Merge existing assistant message metadata as initial state when `message(...)` is provided.
- [x] 3.2 Use `metadataSupplier` as initial state when no existing message is provided.
- [x] 3.3 Merge `StartChunk.messageMetadata` into response message metadata.
- [x] 3.4 Merge `MessageMetadataChunk.messageMetadata` into response message metadata.
- [x] 3.5 Merge `FinishChunk.messageMetadata` before final response message is exposed.
- [x] 3.6 Emit `messages()` snapshots when metadata changes.
- [x] 3.7 Do not emit metadata-only snapshots for null or unchanged metadata updates.
- [x] 3.8 Ensure metadata updates do not enter `UIMessage.parts`.
- [x] 3.9 Keep terminal and finish APIs free of a separate metadata field.

## 4. Tests

- [x] 4.1 Add tests for start metadata aggregation.
- [x] 4.2 Add tests for message-metadata chunk aggregation and snapshot emission.
- [x] 4.3 Add tests for finish metadata aggregation into final response message.
- [x] 4.4 Add tests for existing message metadata as initial state.
- [x] 4.5 Add tests for metadata supplier as initial state.
- [x] 4.6 Add tests for Map shallow merge with later keys overriding earlier keys.
- [x] 4.7 Add tests for non-Map metadata replacement.
- [x] 4.8 Add tests for custom metadata merger on reader options.
- [x] 4.9 Add tests for custom metadata merger on stream creation finish aggregation.
- [x] 4.10 Add tests that metadata updates do not create message parts.
- [x] 4.11 Add tests that `StreamTextResult.toUIMessageStream()` does not auto-promote model metadata.

## 5. Documentation

- [x] 5.1 Update `dev/ui-message-stream.md` with start, message-metadata, and finish metadata examples.
- [x] 5.2 Document default Map shallow merge and non-Map replacement behavior.
- [x] 5.3 Document custom metadata merger usage for typed metadata.
- [x] 5.4 Document that metadata chunks update `UIMessage.metadata` and do not enter `UIMessage.parts`.
- [x] 5.5 Document the boundary between message metadata, `DataPart`, and transient data.
- [x] 5.6 Document non-goals: no Chat Handler shortcut, no automatic model metadata promotion, no terminal metadata field.

## 6. Validation

- [x] 6.1 Run focused UI message stream reader tests.
- [x] 6.2 Run focused UI message stream writer/creation tests.
- [x] 6.3 Run focused stream text UI conversion tests.
- [x] 6.4 Run `./gradlew :api:compileJava`.
- [x] 6.5 Run `./gradlew test`.
- [x] 6.6 Run OpenSpec validation for changed specs.
- [x] 6.7 Run `git diff --check`.
