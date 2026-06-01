## Context

`LanguageModel.streamText` currently has two execution paths: plain requests stream directly from `ChatModel.stream`, while requests with tools are delegated to `generateText` and converted back into stream parts after completion. That shortcut preserved multi-step tool execution, but it removed progressive rendering in the Console workbench and made tool-enabled chat feel non-streaming.

provider-neutral AI API treats tool calling as a step boundary inside streaming generation: model deltas are emitted until the model finishes with tool calls, tools execute, tool results are emitted, and the next model step streams if the stop condition allows. Halo should follow that behavior model while keeping Halo-owned request/response DTOs and stream part names.

## Goals / Non-Goals

**Goals:**

- Keep `streamText` truly streaming when tools are present.
- Emit step lifecycle, reasoning deltas, text deltas, tool calls, tool results, tool errors, and finish parts in the order they become available.
- Preserve reasoning content and provider metadata needed for follow-up tool continuations.
- Keep the implementation provider-neutral in `LanguageModelImpl`; provider-specific request shaping remains behind provider options/adapters.
- Make the Console workbench useful for observing tool activity without waiting for the final answer.

**Non-Goals:**

- No provider-neutral AI API compatibility mode and no third-party stream headers.
- No client-side tool approval, browser-executed tools, or deferred tool result submission.
- No provider-specific branching in generic service code.
- No compatibility shim for the previous buffered tool streaming behavior.

## Decisions

1. Add a dedicated streaming tool loop instead of reusing `generateText`.

   `streamText` will build messages once, then iterate provider steps up to `maxSteps`. Each step calls `chatModel.stream(prompt)`, forwards stream parts as chunks arrive, and also accumulates the step output so the service can decide whether to execute tools and continue. This keeps non-streaming aggregation and streaming orchestration separate, which matches their different latency requirements.

   Alternative considered: keep using `generateText` and flush synthetic deltas earlier. That cannot expose provider deltas progressively because the upstream call is already complete by the time parts are generated.

2. Treat tool calls as step-completion data.

   Providers commonly emit final tool calls at the end of a model step, while some can stream tool input deltas. Halo will require completed `tool-call` parts at step completion for execution, and may add optional tool-call input lifecycle parts only if the current Spring AI stream exposes reliable partial arguments. The first implementation should not invent partial tool deltas from unavailable data.

   Alternative considered: always add external provider-neutral `tool-call-streaming-start` and `tool-call-delta` equivalents. That would be misleading for providers that only expose completed calls through Spring AI.

3. Execute tools between streamed model steps.

   After a step finishes with executable tool calls and `maxSteps` allows continuation, the service emits `tool-call`, executes the server-side executor, emits `tool-result` or `tool-error`, appends assistant and tool result messages, then starts the next streamed provider step. Tool execution is still server-side and request-scoped.

   Alternative considered: execute tools as soon as a call appears in a chunk. That is risky because arguments may still be incomplete; completed step data is a safer contract.

4. Preserve reasoning for continuation from the accumulated step output.

   The stream path must aggregate reasoning metadata from chunks into the assistant message that is appended before tool results. This mirrors the non-streaming reasoning fix and avoids DeepSeek-style thinking mode failures without adding provider checks to the generic code.

5. Keep stream failures protocol-safe.

   Any validation or provider error should be emitted as a Halo `error` part followed by normal stream completion. If the failure happens after a step has started, the stream should avoid emitting duplicate terminal parts for that step.

## Risks / Trade-offs

- [Risk] Spring AI may not expose partial tool-call argument deltas consistently across providers. → Mitigation: require completed `tool-call` parts and add optional partial lifecycle only when backed by real provider data.
- [Risk] Streaming orchestration duplicates some mapping logic from `generateText`. → Mitigation: extract shared step accumulation/mapping helpers so both paths use the same reasoning, usage, and metadata mapping rules.
- [Risk] Tool executors can be slow and make the stream appear paused. → Mitigation: emit tool-call before execution and tool-result/tool-error immediately after execution so the UI can show activity during the pause.
- [Risk] Multi-step streams can emit several text IDs and reasoning IDs. → Mitigation: scope IDs per step and assert ordering in backend tests.
