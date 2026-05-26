## Why

The public language-model API currently exposes `chat(String)`, `streamChat(ChatRequest)`, and `ChatChunk`. That shape is useful for a first streaming prototype, but it leaves several important caller-facing contracts under-specified:

- callers cannot rely on a complete, model-independent result object for text, finish reason, usage, and provider metadata;
- streaming chunks are implementation-shaped rather than protocol-shaped, making HTTP and Java consumers share an accidental DTO;
- request messages are plain role/content strings, which will be hard to evolve toward AI SDK-style model messages, tools, and multimodal parts;
- provider-specific options are present but not strongly namespaced in the language-model contract;
- the Console test-chat endpoint exposes the current `ChatChunk` shape rather than a Halo-owned streaming protocol.

AI Foundation should make the caller contract stable before other plugins build on it. The new contract should align with AI SDK's `generateText` / `streamText` concepts while staying independent of Vercel-specific headers and independent of Spring AI public types.

## What Changes

- **BREAKING**: Replace the public language-model methods with `generateText` and `streamText`.
- **BREAKING**: Replace `ChatRequest`, `Message`, `ChatChunk`, `ChunkType`, and the chat-oriented usage DTO in the public language-model path with text-generation request/result/stream DTOs.
- Introduce AI SDK-inspired `ModelMessage` and content-part types. V1 accepts text parts only, while preserving an extensible shape for future image, file, tool, and reasoning support.
- Introduce `GenerateTextResult` with unified `text`, `finishReason`, `rawFinishReason`, `usage`, and `providerMetadata`.
- Introduce `TextStreamPart` with Halo-owned event types: `start`, `text-start`, `text-delta`, `text-end`, `finish`, and `error`.
- Define Halo HTTP text stream protocol `text-v1`, identified by `X-Halo-AI-Stream-Protocol: text-v1`, not by `x-vercel-ai-ui-message-stream`.
- Update the Console test-chat stream endpoint and model test workbench to consume Halo text stream parts instead of `ChatChunk`.

## Capabilities

### Modified Capabilities

- `ai-model-service`: Replace chat/streamChat contracts with model-independent text generation and text streaming contracts.
- `test-chat-streaming`: Replace `ChatChunk` SSE with Halo text stream SSE.
- `model-test-workbench`: Consume Halo text stream parts in the test workbench.

## Non-Goals

- This change does not implement tool calling, tool execution loops, structured output, multimodal model invocation, source/document streaming, or reasoning streaming.
- This change does not provide compatibility shims for `chat`, `streamChat`, `ChatRequest`, or `ChatChunk`, because the plugin is unreleased.
- This change does not expose Spring AI request, response, option, message, or usage types through the public API.
- This change does not attempt to be wire-compatible with AI SDK UI. It borrows useful stream-part semantics but uses Halo protocol identity.
- This change does not add role-specific permission configuration; Console usage remains scoped to super administrators.

## Impact

- `api/` will gain new language-model DTOs and remove the old chat DTOs from the intended public surface.
- `app/` will map the new request/result/stream DTOs to and from Spring AI `Prompt`, `ChatOptions`, and `ChatResponse`.
- Console streaming tests and frontend stream parsing will need to switch from `ChatChunk` objects to Halo text stream JSON parts.
- OpenAPI-generated frontend clients may change for the Console test endpoint after backend endpoint fields are updated.
