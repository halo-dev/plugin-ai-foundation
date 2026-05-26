## Why

The current text generation API establishes `generateText` and `streamText`, but its result and stream model are still thinner than the AI SDK Core shape that inspired it. Before adding tools, multimodal input, or structured output, the core contract needs richer provider-neutral metadata, step boundaries, warnings, and stream lifecycle events so caller code can remain stable as provider capabilities grow.

## What Changes

- Extend the public `GenerateTextResult` model with AI SDK-inspired fields for content parts, warnings, request metadata, response metadata, step results, total usage, and provider metadata.
- Extend `TextStreamPart` with lifecycle and diagnostic part types such as `start-step`, `finish-step`, and `raw`, while preserving existing text delta behavior.
- Introduce provider-neutral DTOs for generation content, generation warnings, request/response metadata, and generation steps without exposing Spring AI or provider-native types.
- Update the console test stream contract and workbench client behavior to tolerate richer stream parts while continuing to render `text-delta` progressively.
- Document which richer AI SDK concepts are intentionally reserved for later work.

Non-goals:

- Implement tool execution, automatic multi-step tool loops, or `stopWhen` behavior.
- Implement multimodal provider invocation for image, file, source, or reasoning content.
- Implement `generateObject` or `streamObject` structured output APIs.
- Copy AI SDK UI's Vercel-specific HTTP headers or exact wire protocol identity.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-model-service`: enrich the public text generation result, stream part, and metadata contracts for `LanguageModel.generateText()` and `LanguageModel.streamText()`.
- `test-chat-streaming`: allow the console streaming endpoint to expose richer Halo text stream parts while preserving the existing SSE transport contract.
- `model-test-workbench`: make the workbench consume richer stream events without breaking progressive text rendering.

## Impact

- `api/`: new and extended DTOs for text generation result content, steps, warnings, request/response metadata, usage, and stream parts.
- `app/`: `LanguageModelImpl` mapping from Spring AI responses/stream chunks to the richer Halo DTOs.
- `app/endpoint`: streaming test endpoint documentation and behavior remains Halo-owned with `X-Halo-AI-Stream-Protocol`, but may emit additional part types.
- `ui/`: generated API client and test workbench stream handling must ignore unknown/non-renderable parts and handle new lifecycle events.
- `dev/dev.md`: developer-facing examples and field reference should describe the richer core text generation contract.
