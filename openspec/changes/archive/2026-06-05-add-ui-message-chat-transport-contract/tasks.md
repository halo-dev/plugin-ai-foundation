## 1. Request Contract API

- [x] 1.1 Add `UIMessageChatTrigger` with submit and regenerate values using Halo Java API naming style.
- [x] 1.2 Add `UIMessageChatRequest<M>` with chat id, typed UI messages, trigger, and optional message id.
- [x] 1.3 Add required-field validation helpers for chat id, messages, and trigger.
- [x] 1.4 Ensure the new request API remains in the `api` module and does not reference WebFlux, Servlet, Jackson, or Halo runtime types.

## 2. Handler Integration

- [x] 2.1 Add a `UIMessageChatHandlers.streamText(...)` entry point or option path that accepts `UIMessageChatRequest<M>`.
- [x] 2.2 For submit requests, use the provided messages as the effective message history.
- [x] 2.3 For regenerate requests, require `messageId` before model invocation.
- [x] 2.4 For regenerate requests, reject missing target messages before model invocation.
- [x] 2.5 For regenerate requests, reject non-assistant target messages before model invocation.
- [x] 2.6 For regenerate requests, trim the target assistant message and all later messages before validation, conversion, model invocation, and finish aggregation.
- [x] 2.7 Preserve existing request customizer, validation customizer, conversion customizer, serializer, error handling, and finish behavior when the handler starts from a chat request.
- [x] 2.8 Keep provider retry behavior controlled only by generation request settings such as `maxRetries`.

## 3. Tests

- [x] 3.1 Add tests that submit requests stream from the provided message history.
- [x] 3.2 Add tests that regenerate requests require `messageId`.
- [x] 3.3 Add tests that regenerate requests reject unknown message ids.
- [x] 3.4 Add tests that regenerate requests reject non-assistant message ids.
- [x] 3.5 Add tests that regenerate requests trim the old assistant response and later messages before model invocation.
- [x] 3.6 Add tests that finish aggregation for regenerate requests uses the effective trimmed history.
- [x] 3.7 Add tests that request customizers and `maxRetries` still behave as generation settings rather than transport trigger behavior.
- [x] 3.8 Remove the stale documentation guardrail test instead of maintaining a manual public type registry.

## 4. Documentation

- [x] 4.1 Update `dev/ui-message-stream.md` with the default chat request body shape.
- [x] 4.2 Document manual WebFlux glue code for parsing `UIMessageChatRequest<M>` and returning `UIMessageStreamResponse`.
- [x] 4.3 Document submit versus regenerate semantics.
- [x] 4.4 Document that regenerate is user-level re-generation, not provider retry.
- [x] 4.5 Document that stop/abort is transport cancellation, not a trigger.
- [x] 4.6 Document that resume stream, active stream registry, and replay strategy are deferred.
- [x] 4.7 Document how callers can wrap the request model for custom endpoint fields without a fixed extra body or metadata protocol.

## 5. Validation

- [x] 5.1 Run focused UI message chat handler tests.
- [x] 5.2 Confirm the stale documentation guardrail test has been removed.
- [x] 5.3 Run `./gradlew :api:compileJava`.
- [x] 5.4 Run `./gradlew test`.
- [x] 5.5 Run OpenSpec validation for changed specs.
- [x] 5.6 Run `git diff --check`.
