## Context

The SDK already has `UIMessage<M>.metadata` and `metadataSupplier` on stream reader and stream creation options. That only sets metadata when the response message is first materialized. AI SDK UI also allows message metadata to flow through stream chunks at start, during the stream, and at finish, and treats metadata changes as message state updates.

This design adds the same backend Java protocol surface while preserving the existing framework-neutral `api` module and without extending chat handler ergonomics yet.

## Goals / Non-Goals

**Goals:**

- Represent message-level metadata on UI stream chunks.
- Let callers write metadata updates through `UIMessageStreamWriter`.
- Aggregate metadata updates into `UIMessage<M>.metadata`.
- Trigger reader snapshots when metadata changes.
- Let callers customize metadata merge behavior for typed Java metadata.
- Preserve current `metadataSupplier` behavior as the initial metadata source.
- Keep metadata out of `UIMessage.parts`.

**Non-Goals:**

- No `UIMessageChatOptions` shortcut or chat stream hook.
- No automatic metadata mapping from `StreamTextResult`.
- No `finish.metadata()` or `UIMessageStreamTerminal` metadata field.
- No frontend npm helper.
- No WebFlux adapter.
- No resume stream behavior.

## Decisions

### Use AI SDK-compatible wire names

Add `messageMetadata` to `StartChunk` and `FinishChunk`, and add a new `MessageMetadataChunk` whose type is `message-metadata`.

Use `messageMetadata` instead of a generic `metadata` field to avoid confusion with provider metadata, request metadata, response metadata, or error metadata. This also keeps the wire shape close to AI SDK UI and makes a future npm helper simpler.

### Keep metadata message-level

Metadata chunks update `UIMessage.metadata` only. They do not create `DataPart` and do not enter `UIMessage.parts`.

`DataPart` remains the right place for message content or business blocks that should be part of the visible/persistent message content. Message metadata is a single message-level property bag or typed value.

### Preserve supplier as initial metadata

The reader starts from an existing assistant message metadata when `message(...)` is provided. Otherwise, the first time metadata is needed, it calls `metadataSupplier`.

If the stream has no metadata chunks, the final response message still uses the supplier result as before. If metadata chunks arrive, the supplier result is merged first, then incoming metadata is merged in stream order.

### Merge by default, customize when typed

Introduce `UIMessageMetadataMerger<M>`:

```java
@FunctionalInterface
public interface UIMessageMetadataMerger<M> {
    M merge(M current, Object update);
}
```

Default behavior:

- `null` update returns current metadata.
- `null` current returns the update cast to `M`.
- current and update are both `Map<?, ?>`: shallow merge into a new `Map<String, Object>`, with update keys overriding current keys.
- otherwise update replaces current metadata.

This mirrors JavaScript object merge for Map metadata without using reflection for records or POJOs. Callers with typed metadata can provide a custom merger.

### Configure merger on stream reader and stream creation

Add `metadataMerger(...)` to:

- `UIMessageStreamReaderOptions<M>`
- `UIMessageStreamOptions<M>`

`UIMessageStreams.createWithOptions(...)` passes its merger into the internal reader used for finish aggregation.

Do not add it to `UIMessageChatOptions` in this change. Chat handler can keep using the existing stream creation path, and a future change can add a stream customization hook if real callers need it.

### Emit snapshots for metadata changes

When `start.messageMetadata`, `message-metadata`, or `finish.messageMetadata` changes the message metadata, the reader emits a snapshot through `messages()`. If the incoming metadata is `null` or the merge result equals the current metadata, no snapshot is emitted.

This keeps `messages()` as the stream of visible message state changes, where metadata is part of message state even when parts do not change.

## Risks / Trade-offs

- [Risk] Non-Map typed metadata defaults to replacement, which may surprise callers expecting field-level merge. → Mitigation: provide `metadataMerger(...)` and document the default.
- [Risk] Metadata-only snapshots can increase snapshot count. → Mitigation: emit only when merged metadata changes.
- [Risk] Chat handler users cannot inject custom metadata chunks directly. → Mitigation: keep this change focused on protocol primitives and document that chat handler stream hooks are deferred.
- [Risk] Automatic model metadata mapping could be expected. → Mitigation: explicitly document that callers write message metadata intentionally and no model fields are auto-promoted.
