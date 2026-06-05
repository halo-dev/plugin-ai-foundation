## 1. UI Message Models

- [x] 1.1 Add `UIMessageRole` with `SYSTEM`, `USER`, and `ASSISTANT`.
- [x] 1.2 Add generic `UIMessage<M>` with immutable parts, typed metadata, copy helpers, and lightweight query helpers.
- [x] 1.3 Add `UIMessagePart` sealed interface with `type()` and JavaBean `getType()` discriminator accessors.
- [x] 1.4 Add accumulated part records for text, reasoning, data, source URL, file, tool call, tool result, tool error, and tool approval request.
- [x] 1.5 Add `UIMessageParts` factories and typed `DataPart.dataAs(...)` accessor.

## 2. Reader API

- [x] 2.1 Add `UIMessageStreamTerminal` for finish reason, usage, aborted state, and error text.
- [x] 2.2 Add `UIMessageStreamReaderOptions<M>` with stream, existing message, original messages, message id generator, metadata supplier, error handler, and terminate-on-error settings.
- [x] 2.3 Add `ReadUIMessageStreamResult<M>` exposing `messages()`, `responseMessage()`, and `finish()`.
- [x] 2.4 Add `UIMessageStreamReader.read(...)` entry points for simple stream reads, existing-message reads, and configured reads.

## 3. Aggregation Semantics

- [x] 3.1 Implement text and reasoning accumulation by block id.
- [x] 3.2 Implement source, file, tool call, tool result, tool error, tool approval request, and non-transient data replace-by-id semantics.
- [x] 3.3 Exclude transient data, tool input chunks, start, finish-step, finish, error, and abort from `UIMessage.parts`.
- [x] 3.4 Ensure `messages()` emits only when visible message state changes and never emits an initial empty assistant message.
- [x] 3.5 Ensure `responseMessage()` emits a final assistant message even when no visible parts were produced.
- [x] 3.6 Implement id priority: existing message, start chunk, configured generator, SDK default generator.
- [x] 3.7 Implement reader error handling for protocol error chunks, recoverable read failures, and `terminateOnError=true`.

## 4. Stream Finish Integration

- [x] 4.1 Replace the lightweight `UIMessageStreamFinish` shape with updated messages, response message, continuation flag, and `UIMessageStreamTerminal`.
- [x] 4.2 Extend `UIMessageStreamOptions` with original messages, existing message, message id generator, metadata supplier, and typed finish callback.
- [x] 4.3 Update `UIMessageStreams.createWithOptions(...)` to reuse the reader for finish aggregation without duplicating writer execution.
- [x] 4.4 Implement continuation semantics: replace the last assistant message when role and id match; otherwise append response message.

## 5. Tests

- [x] 5.1 Add tests for `UIMessage` query/copy helpers and generic metadata.
- [x] 5.2 Add tests that default JSON serialization includes `type` for `UIMessagePart` records inside `UIMessage`.
- [x] 5.3 Add reader tests for text/reasoning accumulation, visible snapshot emission, and empty response messages.
- [x] 5.4 Add reader tests for replace-by-id semantics across source, file, tool, approval, and data parts.
- [x] 5.5 Add reader tests for transient data, tool input, and lifecycle chunk exclusion.
- [x] 5.6 Add reader tests for id priority, existing message resume, metadata supplier reuse, and immutable snapshots.
- [x] 5.7 Add reader tests for protocol error chunks, recoverable read failures, and terminate-on-error failures.
- [x] 5.8 Add stream creation tests for full `onFinish` messages, response message, continuation, and terminal summary.

## 6. Documentation

- [x] 6.1 Update `dev/dev.md` with `UIMessage`, `UIMessagePart`, and reader usage.
- [x] 6.2 Document persistence flow with `originalMessages`, `responseMessage`, and `isContinuation`.
- [x] 6.3 Document transient data and tool-input aggregation exclusions.
- [x] 6.4 Document that frontend npm helper and `UIMessage` to `ModelMessage` conversion remain deferred.

## 7. Validation

- [x] 7.1 Run focused UI message aggregation tests.
- [x] 7.2 Run `./gradlew :api:compileJava`.
- [x] 7.3 Run `./gradlew test`.
- [x] 7.4 Run `openspec validate ui-message-stream --type spec`.
- [x] 7.5 Run `git diff --check`.
