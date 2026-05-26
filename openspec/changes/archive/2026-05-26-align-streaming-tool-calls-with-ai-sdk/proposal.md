## Why

Current `streamText` requests that include tools are internally routed through `generateText`, so the backend waits for all model steps and tool execution to finish before emitting stream parts. This breaks the expected interactive behavior: tool calling should introduce step boundaries and execution pauses, not turn the whole response into a buffered non-streaming response.

## What Changes

- Implement a true streaming multi-step tool loop for `LanguageModel.streamText(GenerateTextRequest)`.
- Emit model deltas, reasoning deltas, tool calls, tool results, tool errors, step finish parts, and final finish parts progressively as each step completes.
- Keep `generateText` as the non-streaming aggregation API while making `streamText` use its own streaming execution path.
- Preserve reasoning content needed for follow-up requests during streamed tool continuations.
- Add tests for progressive ordering, multi-step streaming, tool execution failures, and max-step behavior.

Non-goals:

- Do not implement AI SDK compatibility mode or reuse Vercel response headers.
- Do not introduce provider-specific checks in generic language model service code.
- Do not add client-side tool approval or browser-executed tools in this change.
- Do not change public request field names unless the streaming tool lifecycle requires a new Halo-owned stream part.

## Capabilities

### New Capabilities

- `streaming-tool-calls`: Server-side tool calling in `streamText`, including progressive step streaming and multi-step continuation.

### Modified Capabilities

- `ai-model-service`: `LanguageModel.streamText` behavior changes from buffered tool output to true streaming tool execution.
- `test-chat-streaming`: Console streaming test endpoint and workbench must consume progressively emitted tool lifecycle parts.

## Impact

- `api/`: May add or clarify `TextStreamPart` types for streaming tool-call lifecycle if the current part model is insufficient.
- `app/`: `LanguageModelImpl.streamText` needs an independent streaming multi-step executor that can aggregate each streamed step enough to continue tool calls while still forwarding parts progressively.
- `ui/`: The model test workbench should keep rendering partial output while tool calls execute and show tool activity as it arrives.
- Tests: Backend service and endpoint tests need to assert event ordering; frontend parser/workbench tests need to cover progressive tool activity.
