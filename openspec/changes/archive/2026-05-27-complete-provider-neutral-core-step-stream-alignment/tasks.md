## 1. API Contracts

- [x] 1.1 Add provider-neutral step control types for stop conditions, prepared step overrides, step context, and prepare-step callback.
- [x] 1.2 Extend `GenerateTextRequest` with `stopWhen` and `prepareStep` as the only step-control entry points.
- [x] 1.3 Extend `TextStreamPart` and related content-part models for `source`, `file`, `tool-input-start`, and `tool-input-delta`.
- [x] 1.4 Add `StreamTextResult` convenience projections for text, reasoning, usage, total usage, finish reasons, warnings, steps, tool calls/results/errors, request, response, provider metadata, content, reasoning parts, and output.
- [x] 1.5 Add JavaDoc examples for step control, stream result projections, new stream parts, and source/file result parts.

## 2. Step Orchestration

- [x] 2.1 Resolve `stopWhen` before generation starts and use single-step behavior when omitted.
- [x] 2.2 Invoke `prepareStep` before every model step and apply returned messages, tool choice, active tools, provider options, model settings, and stop condition overrides.
- [x] 2.3 Validate prepared active tool names before provider invocation.
- [x] 2.4 Ensure streaming and non-streaming generation use the same step orchestration path.
- [x] 2.5 Record prepared settings and produced tool calls/results/errors in the correct `GenerationStep`.

## 3. Stream Protocol Normalization

- [x] 3.1 Introduce a centralized stream protocol normalizer that tracks open text, reasoning, and tool-input blocks.
- [x] 3.2 Ensure incompatible blocks are closed before a new block opens and invalid unrecoverable transitions emit sanitized errors.
- [x] 3.3 Map provider source and generated-file data into canonical result content and full-stream parts when available.
- [x] 3.4 Map incremental provider tool argument chunks into `tool-input-start` and `tool-input-delta` without synthesizing fake deltas for providers that only expose complete tool calls.
- [x] 3.5 Ensure `start`, `start-step`, `finish-step`, `finish`, `error`, and `[DONE]` ordering remains canonical for the console endpoint.

## 4. Stream Result Sharing

- [x] 4.1 Refactor `StreamTextResult` construction so all projections share one provider execution.
- [x] 4.2 Cache final generation result for late subscribers to final projections.
- [x] 4.3 Ensure `textStream`, `partialOutputStream`, and `elementStream` derive from the shared stream without triggering duplicate provider calls.
- [x] 4.4 Ensure final projection failures match full-stream error semantics.

## 5. Structured Output

- [x] 5.1 Preserve JSON text streaming for object, array, JSON, and choice output modes.
- [x] 5.2 Keep `partialOutputStream` best-effort and unvalidated for incomplete values.
- [x] 5.3 Ensure `elementStream` emits only complete validated array elements.
- [x] 5.4 Return structured validation error details for invalid final output or invalid completed elements.

## 6. Console Test Workbench

- [x] 6.1 Refresh generated API client if request or stream part schemas change.
- [x] 6.2 Update the console stream parser to tolerate source, file, tool-input-start, and tool-input-delta events.
- [x] 6.3 Keep reasoning, tools, and final answer displayed in provider stream order.
- [x] 6.4 Preserve unknown future event handling without breaking the stream.

## 7. Tests and Validation

- [x] 7.1 Add reusable stream protocol invariant tests for non-overlapping blocks and step lifecycle ordering.
- [x] 7.2 Add backend tests for stop conditions, prepare-step overrides, active tool validation, and shared streaming/non-streaming orchestration.
- [x] 7.3 Add backend tests proving multiple `StreamTextResult` projections do not duplicate provider invocations.
- [x] 7.4 Add structured output tests for partial output, validated element output, and validation failures.
- [x] 7.5 Add UI parser tests for new event types and unknown event tolerance.
- [x] 7.6 Run `openspec validate --all --strict`.
- [x] 7.7 Run `./gradlew :app:test`.
- [x] 7.8 Run `pnpm --dir ui type-check` and targeted UI tests for the model test workbench.
