## 1. Cancellation Public API

- [x] 1.1 Add a framework-neutral UI message cancellation helper type that exposes `token()`, `cancel()`, and `isCancellationRequested()`.
- [x] 1.2 Add a factory utility for creating UI message cancellation helpers.
- [x] 1.3 Add Reactor `Flux` subscriber-cancel binding that cancels the helper only on `SignalType.CANCEL`.
- [x] 1.4 Add Reactor `Mono` subscriber-cancel binding that cancels the helper only on `SignalType.CANCEL`.
- [x] 1.5 Add unit tests for helper initial state, manual cancellation, Flux cancellation, Mono cancellation, normal completion, and publisher error behavior.

## 2. Chat Handler Cancellation Wiring

- [x] 2.1 Add `UIMessageChatOptions.cancellationToken(...)` and internal getter.
- [x] 2.2 Inject the configured token into the final `GenerateTextRequest` created by `UIMessageChatHandlers`.
- [x] 2.3 Reject request customizers that set `cancellationToken`.
- [x] 2.4 Add chat handler tests for token injection, missing-token behavior, and customizer rejection.

## 3. UI Stream Abort Mapping

- [x] 3.1 Add cancellation detection for recognized generation cancellation exceptions and configured cancelled tokens.
- [x] 3.2 Map cancellation detected during UI stream creation or merged stream execution to `UIMessageChunks.abort()`.
- [x] 3.3 Ensure cancellation abort mapping bypasses the configured safe error text handler.
- [x] 3.4 Preserve existing error chunk behavior for non-cancellation stream failures.
- [x] 3.5 Add tests for cancellation exception to abort, cancelled-token failure to abort, error handler bypass, and non-cancellation error behavior.

## 4. Terminal State And Finish Aggregation

- [x] 4.1 Guard SDK-created streams so `finish`, `error`, and `abort` are emitted at most once.
- [x] 4.2 Ensure cancellation abort finish aggregation exposes the partial response message with `aborted = true`.
- [x] 4.3 Ensure UI message chat `onFinish` runs for cancellation aborts.
- [x] 4.4 Ensure expected cancellation does not populate finish error text.
- [x] 4.5 Add tests for finish-before-cancel, abort-before-finish, error-before-cancel, partial message aggregation, and `onFinish` behavior.

## 5. Consumer Documentation

- [x] 5.1 Update `dev/ui-message-stream.md` with cancellation helper usage.
- [x] 5.2 Document manual WebFlux-style request/response glue with subscriber cancellation binding.
- [x] 5.3 Document abort versus error semantics, partial message persistence, and `onFinish` behavior.
- [x] 5.4 Document deferred stop endpoint, active stream registry, resume/reconnect, cancellation reason, and npm helper work.

## 6. Validation

- [x] 6.1 Run focused UI message stream and chat handler tests.
- [x] 6.2 Run `./gradlew :api:compileJava`.
- [x] 6.3 Run `./gradlew test`.
- [x] 6.4 Run `openspec validate add-ui-message-cancellation-contract --strict`.
- [x] 6.5 Run `git diff --check`.
