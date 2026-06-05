## Why

The first UI message stream API provides typed chunks, writer/merge helpers, and SSE response descriptors, but it still leaves each caller responsible for rebuilding UI state from raw chunks. A backend Java aggregation layer is needed so consumer plugins can turn UI streams into persistent `UIMessage` snapshots and finish results before a separate frontend npm helper is designed.

## What Changes

- Add backend-only Java models for `UIMessage<M>`, `UIMessageRole`, `UIMessagePart`, and typed part records.
- Add `UIMessageStreamReader` that reads a `UIMessageStream` into progressive immutable assistant-message snapshots.
- Add a reader result object exposing live message snapshots, the final response message, and terminal stream summary.
- Support reading from an existing assistant message for resume/continuation scenarios.
- Add reader options for original messages, existing message, message id generation, metadata generation, error handling, and terminate-on-error behavior.
- Upgrade `UIMessageStreams.createWithOptions(...)` finish handling to reuse the reader and return full finish context: updated messages, response message, continuation flag, and terminal summary.
- Keep transient data, tool-input deltas, finish, error, abort, and raw diagnostics out of persisted `UIMessage.parts`.
- Document the backend aggregation lifecycle and explicitly defer npm/frontend helper work to a later change.

### Non-Goals

- Do not add a frontend npm helper package in this change.
- Do not add EventSource/browser parsing helpers.
- Do not add WebFlux endpoint helpers or console endpoints.
- Do not implement `UIMessage` to `ModelMessage` conversion in this change.
- Do not introduce generic typed data/tool part systems beyond generic message metadata.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `ui-message-stream`: Extend the existing UI message stream capability with backend Java UI message aggregation, reader APIs, continuation semantics, and complete finish results.

## Impact

- `api/src/main/java/run/halo/aifoundation/ui/`: new message, part, reader, result, terminal, and option types; updates to existing stream options and finish types.
- `app/src/test/java/run/halo/aifoundation/ui/`: expanded tests for aggregation, continuation, metadata typing, JSON discriminator serialization, and finish results.
- `dev/dev.md`: additional caller-facing documentation for backend UI message aggregation and persistence.
- `openspec/specs/ui-message-stream/spec.md`: updated requirements through this change's delta spec.
