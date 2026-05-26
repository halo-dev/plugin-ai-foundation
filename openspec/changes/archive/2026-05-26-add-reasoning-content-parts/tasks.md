## 1. Public API Model

- [x] 1.1 Add reasoning part DTOs and `PartType` constants for generated content, message history, and stream events.
- [x] 1.2 Extend `GenerateTextResult`, `GenerationStep`, and usage DTOs with `reasoning`, `reasoningText`, and `reasoningTokens` fields.
- [x] 1.3 Add JavaDoc and examples for reasoning parts, provider metadata, and reasoning history round-trip behavior.
- [x] 1.4 Update request validation so unsupported reasoning history is rejected before provider invocation.

## 2. Provider Mapping

- [x] 2.1 Extract reasoning content and reasoning token usage from Spring AI/OpenAI-compatible responses when available.
- [x] 2.2 Preserve provider-specific reasoning metadata needed for follow-up requests without exposing provider-native classes.
- [x] 2.3 Convert assistant reasoning parts back into provider request messages for supported providers.
- [x] 2.4 Fix DeepSeek thinking-mode tool continuation by passing required `reasoning_content` on follow-up requests.

## 3. Generation Flow

- [x] 3.1 Include reasoning parts and reasoning aggregates in non-streaming `generateText` results.
- [x] 3.2 Include reasoning parts and reasoning aggregates in each `GenerationStep`.
- [x] 3.3 Preserve reasoning content during multi-step tool loops when assistant tool calls are added to history.
- [x] 3.4 Aggregate reasoning token usage across steps into total usage.

## 4. Streaming Protocol

- [x] 4.1 Add reasoning stream parts for reasoning start, reasoning delta, and reasoning end events.
- [x] 4.2 Emit reasoning parts separately from text deltas in `streamText`.
- [x] 4.3 Include reasoning token usage in `finish-step` and final `finish` parts when available.
- [x] 4.4 Ensure stream error handling still emits safe `error` and `[DONE]` frames after reasoning parts.

## 5. Console Workbench

- [x] 5.1 Regenerate the OpenAPI TypeScript client after backend API field changes.
- [x] 5.2 Update the stream parser to recognize reasoning parts and keep answer text separate.
- [x] 5.3 Add optional reasoning display associated with the active assistant message.
- [x] 5.4 Ensure unknown provider metadata on reasoning parts is ignored by the frontend.

## 6. Tests and Documentation

- [x] 6.1 Add backend tests for reasoning result mapping, reasoning history validation, and reasoning token usage.
- [x] 6.2 Add backend tests for DeepSeek/OpenAI-compatible reasoning round-trip with tool calls.
- [x] 6.3 Add streaming endpoint tests for reasoning part serialization and post-reasoning errors.
- [x] 6.4 Add frontend parser/workbench tests for reasoning parts.
- [x] 6.5 Update `dev/dev.md` with reasoning-capable model examples and caveats.
- [x] 6.6 Run `./gradlew :app:test`, `./gradlew generateApiClient`, and `pnpm --dir ui type-check`.
