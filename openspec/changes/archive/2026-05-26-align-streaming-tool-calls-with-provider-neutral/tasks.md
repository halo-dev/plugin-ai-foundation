## 1. Stream Tool Execution Core

- [x] 1.1 Split `LanguageModelImpl.streamText` tool handling away from `generateText` result replay.
- [x] 1.2 Add a streamed step accumulator that captures text, reasoning, tool calls, finish reason, usage, warnings, request metadata, response metadata, and provider metadata while forwarding parts progressively.
- [x] 1.3 Emit per-step `start-step`, reasoning, text, raw diagnostic, `finish-step`, and final `finish` parts with stable ordering and per-step IDs.
- [x] 1.4 Aggregate usage across streamed steps for the final `finish` part.

## 2. Tool Loop Behavior

- [x] 2.1 Emit completed `tool-call` parts before executing server-side tools.
- [x] 2.2 Execute request-scoped tool executors between streamed provider steps and emit `tool-result` or `tool-error` progressively.
- [x] 2.3 Append assistant tool-call history and tool result history before the next streamed provider step.
- [x] 2.4 Stop the stream loop correctly for no tool calls, tool errors, missing executors, unknown tools, and max-step exhaustion.
- [x] 2.5 Preserve streamed reasoning content and provider metadata in assistant continuation messages without provider-specific checks in generic code.

## 3. Console Workbench

- [x] 3.1 Ensure the test-chat SSE endpoint remains a pass-through for progressively emitted `TextStreamPart` values.
- [x] 3.2 Update workbench state handling if needed so tool calls/results/errors remain attached to the active assistant message across multiple streamed steps.
- [x] 3.3 Keep assistant loading state active during tool execution pauses until `finish`, `error`, abort, or `[DONE]`.

## 4. Tests

- [x] 4.1 Add backend tests proving tool-enabled `streamText` emits early text or reasoning deltas before final completion.
- [x] 4.2 Add backend tests for multi-step streamed tool execution ordering: `tool-call`, `tool-result`, next step text, `finish`.
- [x] 4.3 Add backend tests for max-step, tool error, missing executor, and unknown tool stop behavior.
- [x] 4.4 Add backend tests proving reasoning content is preserved when a streamed tool call continues to the next provider step.
- [x] 4.5 Add endpoint and frontend parser/workbench tests for progressive tool activity handling.

## 5. Documentation and Validation

- [x] 5.1 Update `dev/dev.md` to describe streaming tool-call behavior and expected event ordering.
- [x] 5.2 Run `./gradlew :app:test`.
- [x] 5.3 Run UI validation for changed workbench files, such as `pnpm --dir ui type-check` and targeted unit tests.
- [x] 5.4 Run `openspec validate align-streaming-tool-calls-with-provider-neutral --strict`.
