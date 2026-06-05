## Context

The current console model test workbench already validates language model streaming through `ModelConsoleEndpoint.testChatStream(...)` and the Vue workbench. That path uses `GenerateTextRequest`, `StreamTextResult.fullStream()`, `TextStreamPart`, and the `X-Halo-AI-Stream-Protocol: text-v1` response header.

The UI Message backend API now has its own Java stream, chat handler, reader, response descriptor, cancellation helper, and documentation. It still needs a real console path that exercises the same API shape a consumer plugin would use: a `UIMessageChatRequest`, `UIMessageChatHandlers.streamText(...)`, `UIMessageStreamResponse`, UI Message chunks, finish aggregation, regeneration, and cancellation.

The main constraint is maintainability. The workbench should validate both protocols without becoming two separate test applications.

## Goals / Non-Goals

**Goals:**

- Add a console UI Message stream test endpoint while preserving the existing text stream endpoint.
- Reuse backend validation, model resolution, test tool injection, external tool setup, approval setup, and tool-call repair setup.
- Use `UIMessageChatRequest` in the UI Message test path.
- Keep one frontend workbench UI, one parameter panel, and one display model.
- Add only protocol-specific frontend adapters for `TextStreamPart` and `UIMessageChunk`.
- Store `uiMessage` state inside the workbench message model for UI Message mode.
- Support submit, minimal regenerate, and abort in UI Message mode.
- Rewrite `dev/dev.md` into a caller-first guide and keep detailed UI Message content in `dev/ui-message-stream.md`.

**Non-Goals:**

- No npm helper package.
- No public WebFlux adapter.
- No database or browser storage for console test conversations.
- No stop endpoint, active stream registry, resume, reconnect, replay, or stream id contract.
- No provider-aware reasoning preservation helper.
- No separate workbench UI or duplicated parameter panels.
- No attempt to make the internal frontend aggregator a public API.

## Decisions

### Keep backend protocol endpoints separate, share setup

The existing `/models/{name}/test-chat/stream` endpoint remains the text stream test path. A new UI Message test endpoint will be added with a distinct path and response protocol marker.

Both endpoints should share helper methods for:

- resolving the model by `AiModel.metadata.name`
- parsing console test tool options
- injecting console tools
- configuring external tool and repair tests
- request-level generation options where applicable

The protocol-specific difference should stay near the final response construction:

- text stream endpoint returns `TextStreamPart` events
- UI Message endpoint returns `UIMessageChunk` events through `UIMessageStreamResponse`

Alternative considered: replace the existing endpoint with UI Message. Rejected because `fullStream()` and UI Message serve different test purposes and must remain available.

### Use UIMessageChatRequest for the UI Message endpoint

The UI Message endpoint should accept `UIMessageChatRequest<Map<String, Object>>` or an equivalent typed DTO matching the public API shape. This validates the real caller workflow: persisted UI messages are sent to the backend, validated, converted, streamed, aggregated, and optionally regenerated.

The endpoint may still apply shared generation settings from query parameters or a wrapper DTO if needed, but it must not collapse back into `GenerateTextRequest` as the primary UI Message request body.

Alternative considered: accept `GenerateTextRequest` and only call `toUIMessageStreamResponse()`. Rejected because that would not exercise `UIMessageChatHandlers`, validation/conversion, regeneration, or finish aggregation.

### Frontend uses one workbench with protocol adapters

The workbench should add a protocol mode rather than duplicate the screen. Shared state remains:

- selected model
- input area
- message list
- parameter sidebar
- tool toggles
- output settings

Protocol-specific code is isolated in stream request and chunk adapters:

- `TextStreamPart` adapter keeps the existing behavior
- `UIMessageChunk` adapter updates `WorkbenchMessage.uiMessage` and projects it to existing display fields

This keeps maintenance cost low and makes it possible to compare both protocols using the same UI.

### Store uiMessage as source state in UI Message mode

In UI Message mode, each workbench message should keep its source `UIMessage` value. Display fields such as `content`, `reasoningContent`, and `toolEvents` are derived from the source state.

This validates the intended frontend persistence model and avoids lossy reconstruction from text-only display state.

Alternative considered: build `UIMessage` only at send time from `WorkbenchMessage.content`. Rejected because data parts, tool parts, reasoning, and metadata would be lost.

### Add a minimal internal UI Message aggregator

The frontend workbench needs a small internal aggregator to handle streamed chunks:

- text chunks update text parts and display content
- reasoning chunks update reasoning parts and display reasoning text
- non-transient data chunks update `uiMessage.parts`
- transient data chunks can update temporary display state but are not persisted
- message metadata chunks update `uiMessage.metadata`
- tool chunks update persisted tool parts and existing tool event display
- finish/error/abort chunks update message state

The implementation should be intentionally local to the console workbench. It is a proving ground for the future npm helper, not the helper itself.

### Minimal regeneration

UI Message mode should support regenerating an assistant message by sending:

- `trigger = REGENERATE_MESSAGE`
- `messageId = targetAssistant.uiMessage.id`
- `messages = current uiMessage list`

The backend handler owns the truncation semantics. The frontend only needs to replace or update the target assistant message as the new stream arrives.

### Abort uses subscription cancellation

The existing `AbortController` flow remains the frontend stop control. The backend UI Message endpoint should connect subscriber cancellation to `UIMessageCancellation.cancelWhenSubscriberCancels(...)` and pass the token to `UIMessageChatHandlers`.

This validates the cancellation chain without introducing cross-request stop endpoints or active stream registries.

### dev/dev.md becomes a caller workflow guide

The main SDK guide should be reorganized around caller tasks:

1. Connect the SDK.
2. Resolve `AiModelService`.
3. Generate text.
4. Stream text.
5. Use tools.
6. Use structured output.
7. Read reasoning and metadata.
8. Configure cancellation, timeouts, and retries.
9. Use embeddings.
10. Use provider options.
11. Handle errors and test integrations.

Detailed UI Message content remains in `dev/ui-message-stream.md`; the main guide links to it and explains when to use it.

## Risks / Trade-offs

- [Risk] The workbench still grows too much. -> Keep only protocol adapters separate and avoid duplicated Vue screens.
- [Risk] The internal UI Message aggregator is mistaken for a public helper. -> Keep it under workbench utilities and document that npm helper work is deferred.
- [Risk] UI Message request options need generation settings that do not belong in `UIMessageChatRequest`. -> Use a narrow console wrapper or shared parameter mapping only for the test endpoint; do not alter the public API unless implementation proves it necessary.
- [Risk] Cancellation behavior varies by provider. -> Validate that the signal chain is connected and mark partial provider cooperation as a provider/runtime limitation.
- [Risk] Rewriting `dev/dev.md` loses useful details. -> Preserve actual public API examples and move only workflow-level detail into the main guide; keep specialized UI Message detail in the dedicated guide.
