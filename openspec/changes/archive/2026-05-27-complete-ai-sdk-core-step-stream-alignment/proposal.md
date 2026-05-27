## Why

The current text-generation API has the main AI SDK Core surfaces in place, but several behaviors are still weaker than AI SDK's actual contract: multi-step tool loops need provider-neutral `stopWhen` control, stream block ordering relies on scattered logic, and stream result convenience accessors are less complete than callers expect. This change tightens the already implemented feature set so it behaves predictably under real tool, reasoning, and structured-output streams.

## What Changes

- Add provider-neutral `stopWhen` and `prepareStep` style controls for multi-step text generation so callers can decide when tool loops continue and adjust per-step settings.
- Add a reusable stream protocol invariant layer that prevents overlapping lifecycle blocks and verifies text, reasoning, tool, finish, and error ordering.
- Extend stream protocol coverage for AI SDK Core-like events that are currently missing or implicit, especially `source`, `file`, `tool-input-start`, and `tool-input-delta`.
- Add `StreamTextResult` convenience accessors for final text, reasoning, usage, total usage, finish reason, steps, tool calls, tool results, warnings, request, response, and provider metadata without triggering additional model invocations.
- Preserve structured-output behavior while making partial and element stream validation expectations explicit.
- Backend SDK/API change with console test endpoint updates only where needed to expose or verify protocol behavior.

### Non-goals

- Do not add MCP support, model middleware, telemetry, reranking, image generation, video generation, transcription, or speech generation in this change.
- Do not add compatibility shims for old internal stream event shapes; this plugin is unreleased and should keep the contract clean.
- Do not introduce provider-specific branching in public service contracts.

## Capabilities

### New Capabilities

- `step-control`: Provider-neutral controls for multi-step text generation loops, including stopping conditions and per-step preparation.
- `stream-protocol-invariants`: Canonical stream protocol lifecycle rules and validation expectations for Halo full streams.

### Modified Capabilities

- `stream-text-result`: Add convenience final-result projections and require single-execution sharing across all stream/result views.
- `streaming-tool-calls`: Replace max-step-only looping with AI SDK-like step control and expose tool input streaming events when available.
- `structured-output-generation`: Clarify partial output and array element stream validation semantics under the hardened stream result contract.
- `test-chat-streaming`: Ensure console test streaming serializes the strengthened full stream protocol in event order.
- `ai-model-service`: Expose the new request/result fields through the provider-neutral Java SDK without Spring AI types.

## Impact

- `api/`: `GenerateTextRequest`, `StreamTextResult`, `TextStreamPart`, `GenerationStep`, and new step-control contract types.
- `app/`: `LanguageModelImpl` streaming/tool loop orchestration, protocol mapping, structured output projections, and endpoint serialization.
- `ui/`: console model test workbench parser/rendering updates only if new event types are surfaced by the endpoint.
- `openspec/specs/`: delta specs for step control, stream invariants, stream result, tool calls, structured output, test streaming, and model service.
- Validation: backend unit tests, stream protocol invariant tests, UI parser tests, OpenSpec validation, and generated API client refresh if endpoint schema changes.
