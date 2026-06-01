## 1. API Contract

- [x] 1.1 Replace `LanguageModel.chat` and `LanguageModel.streamChat` with `generateText` and `streamText`.
- [x] 1.2 Add `GenerateTextRequest`, `GenerateTextResult`, `ModelMessage`, content part types, `FinishReason`, `LanguageModelUsage`, and `TextStreamPart`.
- [x] 1.3 Remove or stop exposing old language-model DTOs: `ChatRequest`, `Message`, `ChatChunk`, `ChunkType`, and old chat `Usage` naming.
- [x] 1.4 Ensure the public API module still exposes no Spring AI, WebFlux, or Jackson-specific types.

## 2. Request Validation And Mapping

- [x] 2.1 Implement request validation for `prompt` vs `messages`, required content, recognized roles, and V1 text-only parts.
- [x] 2.2 Map top-level `system`, `prompt`, and `ModelMessage` history into Spring AI messages.
- [x] 2.3 Map common generation options into Spring AI `ChatOptions`.
- [x] 2.4 Preserve namespaced `providerOptions` in request DTOs and only apply namespaces explicitly supported by provider adapters.

## 3. Result And Stream Implementation

- [x] 3.1 Implement `generateText(GenerateTextRequest)` and `generateText(String)` in `LanguageModelImpl`.
- [x] 3.2 Map Spring AI text, finish reason, usage, and metadata into `GenerateTextResult`.
- [x] 3.3 Implement `streamText` part sequencing: `start`, `text-start`, `text-delta`, `text-end`, `finish`.
- [x] 3.4 Convert provider/runtime stream failures into `error` parts and graceful stream completion.
- [x] 3.5 Add focused tests for validation, option mapping, finish reason mapping, usage mapping, and stream part order.

## 4. Console Stream Protocol

- [x] 4.1 Update `POST /models/{name}/test-chat/stream` to accept `GenerateTextRequest`-compatible input.
- [x] 4.2 Encode `TextStreamPart` as SSE JSON using `X-Halo-AI-Stream-Protocol: text-v1`.
- [x] 4.3 Ensure the endpoint does not emit `x-vercel-ai-ui-message-stream`.
- [x] 4.4 Append `data: [DONE]` after normal completion and after emitted error parts.
- [x] 4.5 Update OpenAPI operation descriptions and response documentation for Halo text stream protocol.

## 5. Workbench Integration

- [x] 5.1 Update the model test workbench request payload to use `system`, `messages`, `maxOutputTokens`, and namespaced `providerOptions`.
- [x] 5.2 Update frontend SSE parsing from `ChatChunk` to Halo text stream parts.
- [x] 5.3 Preserve progressive Markdown rendering, abort behavior, loading states, and error display.
- [x] 5.4 Regenerate the frontend API client if endpoint request models change.

## 6. Verification

- [x] 6.1 Run `./gradlew :api:compileJava`.
- [x] 6.2 Run `./gradlew :app:test`.
- [x] 6.3 Run frontend type checks if workbench code changes: `cd ui && pnpm type-check`.
- [x] 6.4 Run `openspec validate standardize-text-generation-api --strict`.
