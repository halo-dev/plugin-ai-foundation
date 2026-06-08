## 1. Backend Console UI Message Stream

- [x] 1.1 Refactor `ModelConsoleEndpoint` chat test helpers so text stream and UI Message stream paths share model resolution, request validation, console tool injection, external tool setup, approval setup, and tool-call repair setup.
- [x] 1.2 Add a UI Message chat stream endpoint that accepts a `UIMessageChatRequest`-shaped request body and returns a `UIMessageStreamResponse` SSE body with the Halo UI Message protocol header.
- [x] 1.3 Wire the UI Message endpoint through `UIMessageChatHandlers.streamText(...)`, including serializer, request options, validation/conversion customizers if needed, `onFinish` aggregation, and safe error handling.
- [x] 1.4 Connect subscriber cancellation in the UI Message endpoint through `UIMessageCancellation.cancelWhenSubscriberCancels(...)` and pass the cancellation token to the chat handler.
- [x] 1.5 Preserve the existing text stream endpoint behavior and response header.
- [x] 1.6 Add or update backend tests for the UI Message endpoint, shared setup behavior, protocol headers, submit, regenerate validation, and cancellation wiring where feasible.

## 2. API Client And Frontend Workbench

- [x] 2.1 Regenerate the TypeScript API client if the backend endpoint contract changes.
- [x] 2.2 Add frontend types for workbench UI Message state, chunks, parts, metadata, chat request, and protocol mode without replacing the existing `TextStreamPart` types.
- [x] 2.3 Add an internal UI Message chunk adapter/aggregator for the workbench that handles text, reasoning, non-transient data, transient data, message metadata, tool parts, finish, error, and abort.
- [x] 2.4 Update `WorkbenchMessage` to preserve `uiMessage` source state in UI Message mode and project it to existing display fields.
- [x] 2.5 Update the workbench send flow so text stream and UI Message modes share the same UI, parameter controls, and display model while using protocol-specific request and stream adapters.
- [x] 2.6 Add UI Message mode request creation using `UIMessageChatRequest`, including current UI Message history and submit trigger.
- [x] 2.7 Add minimal UI Message regenerate flow using `trigger = regenerate-message`, target assistant `messageId`, and current UI Message history.
- [x] 2.8 Keep existing text stream mode behavior working.
- [x] 2.9 Add or update frontend utility tests for SSE parsing, UI Message aggregation, request conversion, regeneration request creation, and abort chunk handling.

## 3. Consumer Documentation

- [x] 3.1 Rewrite `dev/dev.md` into a caller-first structure: setup, service resolution, model selection, text generation, streaming, tools, structured output, reasoning/metadata, cancellation/timeouts/retries, embeddings, provider options, errors, and testing.
- [x] 3.2 Reduce unnecessary mixed Chinese/English phrasing in `dev/dev.md` while preserving exact Java API names.
- [x] 3.3 Replace long prose in `dev/dev.md` with shorter sections, small tables, and focused public API examples.
- [x] 3.4 Keep detailed UI Message explanations in `dev/ui-message-stream.md` and link to that guide from the streaming section of `dev/dev.md`.
- [x] 3.5 Clearly mark npm helper, WebFlux adapter, stop endpoint, resume/reconnect, active stream registry, and provider-aware reasoning preservation as deferred work when mentioned.
- [x] 3.6 Update documentation guard tests if existing tests require section names or public type references to match the rewritten guide.

## 4. Validation

- [x] 4.1 Run focused backend tests for `ModelConsoleEndpoint` and UI Message APIs.
- [x] 4.2 Run `./gradlew generateApiClient` if endpoint changes require generated frontend client updates.
- [x] 4.3 Run focused frontend tests for model test workbench utilities.
- [x] 4.4 Run `pnpm -C ui type-check` or the repository-supported equivalent.
- [x] 4.5 Run `./gradlew :api:compileJava`.
- [x] 4.6 Run `./gradlew test`.
- [x] 4.7 Run `openspec validate validate-ui-message-in-console-workbench --strict`.
- [x] 4.8 Run `openspec validate --specs --strict`.
- [x] 4.9 Run `git diff --check`.
