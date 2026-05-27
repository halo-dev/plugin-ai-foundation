## Why

AI Foundation now exposes AI SDK-like text generation, streaming, tools, reasoning, and structured output APIs, but several surfaces are still closer to "shape-compatible" than behavior-compatible. This change hardens the already exposed capabilities so callers can rely on concrete stream results, structured partial output, tool execution context, warnings, and validation errors instead of only seeing matching method names and DTO fields.

## What Changes

- **BREAKING**: Change `LanguageModel.streamText(GenerateTextRequest)` from returning `Flux<TextStreamPart>` directly to returning a `StreamTextResult` that exposes `fullStream`, `textStream`, `partialOutputStream`, `elementStream`, `output`, and final result access.
- Keep the Halo SSE endpoint and Console workbench on the full stream by adapting them to `StreamTextResult.fullStream()`.
- Implement structured streaming semantics aligned with AI SDK Core:
  - `OutputSpec.array(...)` emits validated complete elements through `elementStream`.
  - `OutputSpec.object(...)` and `OutputSpec.json()` emit partial parsed snapshots through `partialOutputStream` when complete JSON prefixes can be parsed.
  - final `output` remains available only after complete validation succeeds.
- Extend tool execution with a provider-neutral `ToolExecutionContext` that includes tool call id, tool name, parsed input, step index, and request messages.
- Add top-level aggregated tool call/result/error fields to generation results for easier caller access.
- Systematize warnings for unsupported, ignored, or downgraded settings and provider mappings, especially structured output strictness and JSON Schema downgrade behavior.
- Enrich structured output validation errors with safe debug context such as output type, output text, validation path, step index, usage, and response metadata.
- Add focused contract tests that prove the implemented behavior, not just the presence of public fields.

### Non-Goals

- Do not add new model types such as image generation, speech, transcription, or video.
- Do not implement MCP tool clients in this change.
- Do not attempt full AI SDK UI stream protocol compatibility; Halo keeps its own stream protocol while aligning Core semantics.
- Do not add step-loop planning features such as `prepareStep`, `activeTools`, or `stopWhen` in this change unless they are required to preserve existing `maxSteps` behavior.

## Capabilities

### New Capabilities
- `stream-text-result`: Structured result object for streaming text generation, including full stream, text-only stream, structured partial output, array element stream, complete output, and final result access.

### Modified Capabilities
- `ai-model-service`: Update the public `LanguageModel` streaming contract, generation result aggregation, tool execution context, warnings, and validation error behavior.
- `structured-output-generation`: Add AI SDK-aligned structured streaming behavior for partial output and array element streams.
- `structured-tool-io`: Expand tool execution semantics to include execution context while preserving input/output schema validation.
- `streaming-tool-calls`: Preserve progressive tool streaming while routing through `StreamTextResult.fullStream()` and retaining step ordering.
- `test-chat-streaming`: Adapt the Console SSE endpoint and workbench to consume full stream from the new streaming result contract.

## Impact

- Affected API module types: `LanguageModel`, `TextStreamPart`, `GenerateTextResult`, `GenerationStep`, `ToolDefinition`, `ToolExecutor`, structured output error types, and new streaming result/context DTOs.
- Affected app implementation: `LanguageModelImpl`, provider option mapping, structured output parsing/validation, tool loop execution, streaming aggregation, and warning generation.
- Affected Console endpoint/UI: model test SSE endpoint, workbench stream parser, structured output controls, and generated TypeScript client after API changes.
- Affected docs/tests: `dev/dev.md`, OpenAPI generated client, backend contract tests, frontend parser/workbench tests, and OpenSpec specs.
