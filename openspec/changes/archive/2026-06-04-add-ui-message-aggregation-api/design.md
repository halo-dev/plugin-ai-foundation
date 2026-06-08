## Context

The current Halo UI message stream API exposes typed `UIMessageChunk` events, writer/merge helpers, response descriptors, and `StreamTextResult` conversion. That is enough to transport UI events to a frontend, but it does not rebuild those events into a persistent message state.

Consumer plugins need a backend Java equivalent of the UI state aggregation layer: a single assistant response message should be reconstructable from chunks, readable as progressive immutable snapshots, resumable from an existing partial message, and available to `onFinish` as a complete response message plus updated conversation messages.

This change remains backend-only. The future npm frontend helper will consume the protocol and message model after the Java semantics settle.

## Goals / Non-Goals

**Goals:**
- Add typed Java `UIMessage<M>` and `UIMessagePart` models for persistent UI state.
- Add a reader that turns `UIMessageStream` chunks into progressive assistant-message snapshots.
- Support resumable aggregation from an existing assistant message.
- Add terminal summary APIs that keep finish/error/abort information separate from message parts.
- Upgrade `UIMessageStreams.createWithOptions(...)` finish handling to return complete `messages`, `responseMessage`, `isContinuation`, and terminal context.
- Keep the existing stream chunk protocol and response descriptor intact.

**Non-Goals:**
- No npm frontend helper package.
- No browser/EventSource parser library.
- No WebFlux endpoint helper or console endpoint.
- No `UIMessage` to `ModelMessage` conversion.
- No generic typed data/tool part system beyond generic message metadata.

## Decisions

### UIMessage represents one message

`UIMessage<M>` represents one UI message, not a conversation container. A conversation is `List<UIMessage<M>>`. Stream aggregation creates or resumes an assistant message.

This matches the intended UI state boundary: the reader emits progressive snapshots of the same response message, while finish handling can return the updated conversation list.

### Generic metadata only

`UIMessage<M>` will genericize message metadata:

```java
public record UIMessage<M>(
    String id,
    UIMessageRole role,
    List<UIMessagePart> parts,
    M metadata
) {}
```

`UIMessagePart` and data/tool payloads remain non-generic in the first version. This gives callers typed message metadata without forcing every mixed part list into complex wildcard generics.

### UIMessagePart is a typed immutable hierarchy

`UIMessagePart` will be a sealed interface with record implementations and a JavaBean `getType()` accessor for serializer compatibility. Parts represent accumulated message state, not raw stream events.

Examples:
- `TextPart(id, text)` accumulates text deltas for a text block.
- `ReasoningPart(id, text, providerMetadata)` accumulates reasoning deltas.
- `DataPart(name, data)` stores non-transient custom data.
- `ToolCallPart`, `ToolResultPart`, `ToolErrorPart`, and `ToolApprovalRequestPart` store stable tool state.

No `TextDeltaPart` or `ToolInputDeltaPart` is persisted.

### Reader returns a result object

The reader will return `ReadUIMessageStreamResult<M>`:

```java
Flux<UIMessage<M>> messages();
Mono<UIMessage<M>> responseMessage();
Mono<UIMessageStreamTerminal> finish();
```

This mirrors the existing pattern of exposing both stream projections and final values. `messages()` emits only when visible message state changes. `responseMessage()` always emits the final assistant message unless `terminateOnError=true` and a fatal read error occurs.

### Message id priority

The response message id is selected by priority:
1. Existing message supplied to the reader.
2. `StartChunk.messageId`.
3. Reader option `generateMessageId`.
4. SDK default generator.

This keeps resume explicit while still allowing simple calls.

### Transient and lifecycle chunks do not become parts

The aggregation layer excludes:
- transient data chunks
- `tool-input-start` / `tool-input-delta`
- `start`
- `finish-step`
- `finish`
- `error`
- `abort`
- raw diagnostics, which are already excluded from UI streams by default

Terminal chunks update `UIMessageStreamTerminal`, not `UIMessage.parts`.

### Append versus replace rules

Text and reasoning use block id accumulation:
- start creates an empty part if absent
- delta appends to the same id
- end does not emit unless visible state changed

Stable state parts replace by id:
- source by `sourceId`
- file by `fileId`
- tool call/result/error by `toolCallId`
- approval request by `approvalId`, falling back to `toolCallId`

This prevents duplicate cards while preserving incremental text behavior.

### Continuation is role and id based

`isContinuation` is true when the last original message is an assistant message and its id equals the response message id. If true, finish handling replaces that last message with the response message. Otherwise it appends the response message.

The reader allows any original message list; a non-assistant last message simply means the response is appended.

### Error handling distinguishes protocol errors from read failures

`ErrorChunk` is a protocol-level event and updates terminal `errorText`. Reader options additionally support `onError` and `terminateOnError` for read failures such as an upstream Flux error or aggregation exception.

Default behavior records safe terminal error text and still emits a final response message. `terminateOnError=true` fails reader projections.

## Risks / Trade-offs

- [Risk] Public API grows many new types. -> Mitigation: keep all types under `run.halo.aifoundation.ui`, use record constructors and factories, and document common paths.
- [Risk] Generic metadata complicates option APIs. -> Mitigation: genericize only `UIMessage<M>` and related reader/finish APIs; keep parts non-generic.
- [Risk] Excluding transient data from snapshots may surprise callers watching `messages()`. -> Mitigation: document that transient data is available at chunk level only.
- [Risk] `onFinish` may consume the stream and affect direct subscribers if implemented naively. -> Mitigation: implement finish aggregation from the same created stream using shared/cached stream projections so writer execution does not duplicate.
- [Risk] Future frontend helper may need slightly different part shapes. -> Mitigation: align names with chunk protocol and keep parts immutable, typed, and serializer-friendly.

## Migration Plan

This is an additive change on an unreleased API. Existing chunk stream callers continue to work.

Implementation order:
1. Add message and part models.
2. Add reader options, terminal, result, and aggregator.
3. Upgrade stream options and finish callback to use reader results.
4. Add tests and documentation.

Rollback is deleting the new aggregation types and reverting the finish callback expansion.

## Open Questions

No open product questions remain for this backend Java phase. Frontend helper design and `UIMessage` to `ModelMessage` conversion are intentionally deferred.
