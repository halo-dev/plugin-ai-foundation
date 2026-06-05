## 1. Chat Handler API

- [x] 1.1 Add `UIMessageChatOptions<M>` with model, messages, continuation message, metadata supplier, message id generator, serializer, request customizer, validation customizer, conversion customizer, finish callback, safe error handler, read error callback, and terminate-on-error settings.
- [x] 1.2 Add `UIMessageChatResult<M>` exposing stream, response, validation result, conversion result, and finish `Mono`.
- [x] 1.3 Add `UIMessageChatHandlers.streamText(...)` entry points.

## 2. Handler Flow

- [x] 2.1 Validate required model and messages options.
- [x] 2.2 Validate UI messages using configured `UIMessageValidators` options and fail fast on invalid input.
- [x] 2.3 Convert UI messages using configured `UIMessageConverters` options and preserve conversion warnings on the result.
- [x] 2.4 Fail before model invocation when converted model messages are empty.
- [x] 2.5 Build `GenerateTextRequest` from request customizer and reject customizers that set `prompt` or `messages`.
- [x] 2.6 Rebuild the final request with converted model messages while preserving non-input request fields.
- [x] 2.7 Invoke `LanguageModel.streamText(...)` with the final request.
- [x] 2.8 Create a UI message stream with original messages, optional continuation message, metadata supplier, message id generator, error options, and merged model UI stream.
- [x] 2.9 Build `UIMessageStreamResponse` with optional serializer.

## 3. Finish Handling

- [x] 3.1 Expose a finish `Mono` backed by the same UI stream creation path.
- [x] 3.2 Invoke caller `onFinish` with the aggregated `UIMessageStreamFinish`.
- [x] 3.3 Make `finish()` fail when caller `onFinish` throws.
- [x] 3.4 Ensure handler does not create a second model stream subscription for finish aggregation.

## 4. Tests

- [x] 4.1 Add tests for successful validate-convert-stream-response flow.
- [x] 4.2 Add tests that conversion warnings are exposed and do not block model invocation.
- [x] 4.3 Add tests for invalid UI messages failing with `InvalidUIMessageException`.
- [x] 4.4 Add tests for empty converted model messages failing before model invocation.
- [x] 4.5 Add tests for request customizer rejection of `prompt` and `messages`.
- [x] 4.6 Add tests that non-input request fields are preserved in the model invocation.
- [x] 4.7 Add tests for original messages, continuation message, metadata supplier, and message id generator wiring.
- [x] 4.8 Add tests for finish `Mono`, caller `onFinish`, and on-finish failure behavior.
- [x] 4.9 Add tests for serializer-backed response body frames.

## 5. Documentation

- [x] 5.1 Update `dev/ui-message-stream.md` with chat handler usage.
- [x] 5.2 Document validation failure, conversion warnings, and empty converted model message behavior.
- [x] 5.3 Document request customizer boundaries and the system-message double-source recommendation.
- [x] 5.4 Document that finish depends on stream consumption.
- [x] 5.5 Document deferred dynamic metadata callback, HTTP transport contract, and transport cancellation/abort mapping.
- [x] 5.6 Add documentation guardrails for chat handler public types and deferred work.

## 6. Validation

- [x] 6.1 Run focused UI message chat handler tests.
- [x] 6.2 Run documentation tests.
- [x] 6.3 Run `./gradlew :api:compileJava`.
- [x] 6.4 Run `./gradlew test`.
- [x] 6.5 Run OpenSpec validation for changed specs.
- [x] 6.6 Run `git diff --check`.
