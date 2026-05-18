## 1. Backend Streaming Contract

- [x] 1.1 Expand `ModelConsoleEndpoint.TestChatRequest` to accept messages, temperature, maxTokens, topP, and providerOptions.
- [x] 1.2 Validate that streaming test-chat requests contain at least one message before calling `LanguageModel.streamChat()`.
- [x] 1.3 Map the expanded request body into `ChatRequest` without collapsing messages into a single prompt.
- [x] 1.4 Preserve SSE error handling by emitting `ChunkType.ERROR` chunks and completing the stream gracefully.
- [x] 1.5 Add or update backend tests for request validation, message mapping, generation parameter mapping, and stream error handling.

## 2. Generated API And Dependencies

- [x] 2.1 Run `sh gradlew generateApiClient` after backend DTO changes.
- [x] 2.2 Add lightweight Markdown rendering and HTML sanitization UI runtime dependencies.
- [x] 2.3 Verify generated TypeScript types expose the expanded test-chat request shape.

## 3. Workbench UI Shell

- [x] 3.1 Add the `测试` tab to `ProviderManager.vue` using the existing route-query tab pattern.
- [x] 3.2 Create the model test workbench view with conversation area, message input, model selector, and parameter panel.
- [x] 3.3 Implement responsive layout behavior for desktop side parameters and mobile constrained viewports.
- [x] 3.4 Add empty state behavior when no enabled chat-capable model is available.

## 4. Model Selection And Message State

- [x] 4.1 Load models through the existing model query path and filter to enabled chat-capable models.
- [x] 4.2 Use `AiModel.metadata.name` as the selected model value and avoid provider-type inference from provider resource names.
- [x] 4.3 Preserve assistant message attribution with the model name and display name used for each streamed response.
- [x] 4.4 Route the existing model-list "测试" action into the new workbench with the chosen model preselected, or remove the modal entry if routing proves unsuitable.

## 5. Parameters And Streaming Chat

- [x] 5.1 Add typed controls for system prompt, temperature, topP, and maxTokens.
- [x] 5.2 Add providerOptions JSON input with client-side validation before send.
- [x] 5.3 Build streaming request payloads from system prompt, conversation messages, common parameters, and parsed providerOptions.
- [x] 5.4 Consume the SSE response with `fetch()` and append TEXT chunks to the active assistant message.
- [x] 5.5 Support aborting an in-progress response while keeping partial output visible.
- [x] 5.6 Display ERROR chunks and network failures as assistant error messages.

## 6. Markdown Rendering

- [x] 6.1 Render assistant messages with `markdown-it` and sanitize rendered HTML with `DOMPurify`.
- [x] 6.2 Keep progressive Markdown output readable as streamed chunks append.
- [x] 6.3 Keep user messages and error messages readable without Markdown style collisions in the Halo Console layout.

## 7. Verification

- [x] 7.1 Run `pnpm test:unit` in `ui`.
- [x] 7.2 Run `sh gradlew test`.
- [x] 7.3 Run `sh gradlew test :ui:pnpmBuild`.
- [x] 7.4 Run `sh gradlew :ui:pnpmCheck`.
- [x] 7.5 Manually verify the workbench with at least one configured chat model, including streaming output, Markdown rendering, model switching, parameter changes, invalid providerOptions JSON, and abort behavior.
