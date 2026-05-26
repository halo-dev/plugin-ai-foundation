## Why

AI Foundation now has model-independent text, reasoning, streaming, and tool calling primitives, but callers still need to parse and validate JSON responses themselves. AI SDK Core treats structured output as part of `generateText` and `streamText`, and this change brings the same provider-neutral contract to Halo so plugins can ask for typed JSON-shaped results without depending on a model vendor.

## What Changes

- Add provider-neutral structured output request types for `GenerateTextRequest`, modeled after AI SDK `Output.text()`, `Output.object()`, `Output.array()`, `Output.choice()`, and `Output.json()`.
- Add structured output fields to `GenerateTextResult`, `GenerationStep`, and stream parts so callers can access final output and, when possible, partial streamed output.
- Validate final structured output against JSON Schema for object/array/choice modes before returning success.
- Support structured output together with tools and multi-step generation: tool steps still run normally, and structured output applies to the final answer step unless explicitly documented otherwise.
- Extend tool definitions to make tool input schemas stricter and to optionally validate tool executor results against a provider-neutral output schema.
- Update console test tooling enough to exercise structured output from the backend test page without hardcoding provider-specific behavior.

Non-goals:

- Do not introduce Zod, Valibot, or TypeScript-specific schema types into the Java API.
- Do not implement AI SDK UI protocol compatibility or Vercel headers.
- Do not add provider-specific branches in `LanguageModelImpl`; provider-specific response-format mapping belongs behind provider options/adapters.
- Do not implement image/audio/video generation in this change.

## Capabilities

### New Capabilities

- `structured-output-generation`: Provider-neutral structured output generation, validation, final result mapping, and stream parts.
- `structured-tool-io`: Tool input/output schema validation and strict-mode behavior for tool definitions.

### Modified Capabilities

- `ai-model-service`: `GenerateTextRequest`, `GenerateTextResult`, `GenerationStep`, and `TextStreamPart` gain structured output behavior.
- `streaming-tool-calls`: Tool-enabled streams must preserve structured output on final answer steps.
- `test-chat-streaming`: Console test endpoint and workbench need to accept structured output options and display structured stream parts.

## Impact

- `api/`: New output request/result DTOs, new `PartType` / `TextStreamPart` values for structured output, additional tool schema fields, and JavaDoc examples.
- `app/`: `LanguageModelImpl` must apply output request validation, provider option mapping, final JSON parsing, JSON Schema validation, and structured stream part emission.
- `ui/`: The test workbench needs minimal controls for selecting structured output mode and entering JSON Schema/options.
- Tests: Backend service tests for object/array/choice/json output, stream partial output, validation failure, and tools with output schemas; frontend parser/workbench tests for structured stream parts.
