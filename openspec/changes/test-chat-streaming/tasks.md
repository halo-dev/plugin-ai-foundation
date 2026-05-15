## 1. Backend SSE Endpoint

- [x] 1.1 Add `POST /models/{name}/test-chat/stream` route to `ModelConsoleEndpoint`
- [x] 1.2 Implement `testChatStream` handler: resolve model, call `languageModel.streamChat(prompt)`, return `Flux<ChatChunk>` with `MediaType.TEXT_EVENT_STREAM`
- [x] 1.3 Add error handling: `onErrorResume` to emit `ChunkType.ERROR` `ChatChunk` and complete stream gracefully
- [x] 1.4 Remove legacy `POST /models/{name}/test-chat` route, handler, and `TestChatRequest` DTO

## 2. Frontend TestChatModal

- [x] 2.1 Replace `useMutation` with `fetch()` + `ReadableStream` SSE parser
- [x] 2.2 Implement progressive rendering: append each `ChatChunk.content` to result as chunks arrive
- [x] 2.3 Add loading state management and `AbortController` for request cancellation
- [x] 2.4 Handle stream errors: display error message when `ChunkType.ERROR` received or connection fails

## 3. Verification

- [x] 3.1 Run `./gradlew compileJava` to verify backend compiles
- [x] 3.2 Run `./gradlew build` to verify full build and tests pass
- [x] 3.3 Run `cd ui && pnpm type-check` to verify frontend types
